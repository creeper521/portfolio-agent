package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalProviderDraftCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compilesBranchSpecificRecommendationDraftIntoCanonicalV5() throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        JsonNode canonical = compiler.compile(mapper.readTree("""
                {
                  "decision":"STANDARD_GOAL",
                  "goal":{
                    "goalKind":"PORTFOLIO_RECOMMEND",
                    "requestedSize":2,
                    "constraints":[]
                  }
                }
                """));

        assertThat(canonical.path("candidateKey").isNull()).isTrue();
        assertThat(canonical.path("clarification").isNull()).isTrue();
        assertThat(canonical.path("recentReference").isNull()).isTrue();
        JsonNode goal = canonical.path("goal");
        assertThat(goal.path("goalKey").textValue())
                .isEqualTo("portfolio-recommend");
        assertThat(goal.path("inputAnchor").path("text").textValue())
                .isEqualTo("给我推荐两个项目");
        assertThat(goal.path("inputAnchor").path("start").intValue()).isZero();
        assertThat(goal.path("requestedOutputs").get(0).textValue())
                .isEqualTo("RECOMMENDATION");
        assertThat(goal.path("knowledgeRequirement").textValue())
                .isEqualTo("PUBLIC_PORTFOLIO_EVIDENCE");
        assertThat(goal.path("parameters").path("kind").textValue())
                .isEqualTo("PORTFOLIO_RECOMMEND");
        assertThat(goal.path("parameters").path("requestedSize").intValue())
                .isEqualTo(2);
    }

    @Test
    void compilesClarificationFromPartialGoalWithoutProviderEchoFields()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "推荐一些项目"));

        JsonNode canonical = compiler.compile(mapper.readTree("""
                {
                  "decision":"NEEDS_CLARIFICATION",
                  "clarification":{
                    "field":"REQUESTED_SIZE",
                    "prompt":"需要推荐几个项目？",
                    "goal":{
                      "goalKind":"PORTFOLIO_RECOMMEND",
                      "constraints":[]
                    }
                  }
                }
                """));

        JsonNode blocked = canonical.path("clarification").path("blockedGoal");
        assertThat(blocked.path("requestedSize").isNull()).isTrue();
        assertThat(blocked.path("requestedOutputs").get(0).textValue())
                .isEqualTo("RECOMMENDATION");
        assertThat(blocked.path("unresolvedField").textValue())
                .isEqualTo("REQUESTED_SIZE");
        assertThat(blocked.path("askedFields").get(0).textValue())
                .isEqualTo("REQUESTED_SIZE");
        assertThat(blocked.path("depth").intValue()).isEqualTo(1);
    }

    @Test
    void rejectsMissingRequiredSemanticFieldWithoutGuessingOrRepair()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {
                  "decision":"STANDARD_GOAL",
                  "goal":{
                    "goalKind":"PORTFOLIO_RECOMMEND"
                  }
                }
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_REQUIRED_FIELD_MISSING");
    }

    @Test
    void rejectsGoalLevelInputTextEchoAsUnknownKeyWithoutEchoingText()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_RECOMMEND",
                  "inputText":"给我推荐两个项目。",
                  "requestedSize":2,"constraints":[]
                }}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .hasMessageNotContaining("给我推荐两个项目")
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_FIELD_CONFLICT_UNKNOWN_KEY");
    }

    @Test
    void derivesBoundedWholeInputAnchorForLongInputs() throws Exception {
        String longText = "详细展开".repeat(100) + "，并解释幂等";

        JsonNode canonical = assertCanonical(longText, """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"GENERAL_EXPLANATION",
                  "topicText":"幂等","depth":"STANDARD"}}
                """);

        JsonNode anchor = canonical.path("goal").path("inputAnchor");
        assertThat(anchor.path("text").textValue())
                .isEqualTo(longText.substring(0, 256));
        assertThat(anchor.path("start").intValue()).isZero();
    }

    @Test
    void reportsClosedSourceLabelWhenSubjectAnchorMissesInput() throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "展开讲讲SQL审计项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_FACT",
                  "subjects":[{"kind":"PROJECT","reference":"sql-audit",
                    "inputText":"SQL 审计项目"}],
                  "facets":["SOLUTION"],
                  "depth":"STANDARD"
                }}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .hasMessageNotContaining("SQL 审计项目")
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_ANCHOR_NOT_FOUND_SUBJECT_INPUT_TEXT_0");
    }

    @Test
    void rejectsFieldsFromAnotherGoalBranchInsteadOfIgnoringThem() throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_RECOMMEND",
                  "requestedSize":2,
                  "constraints":[],
                  "facets":["OVERVIEW"]
                }}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_FIELD_CONFLICT_UNKNOWN_KEY");
    }

    @Test
    void rejectsNonNullSiblingFromAnotherDecisionBranchWithClosedScopeReason()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input("你好"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"CONVERSATIONAL","message":"你好","candidateKey":"C1"}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_FIELD_CONFLICT_CONVERSATIONAL_CANDIDATE_KEY");
    }

    @Test
    void rejectsResolvedFieldInsideClarificationInsteadOfRepairingIt()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "推荐一些项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"NEEDS_CLARIFICATION","clarification":{
                  "field":"REQUESTED_SIZE",
                  "prompt":"需要推荐几个项目？",
                  "goal":{
                    "goalKind":"PORTFOLIO_RECOMMEND",
                    "requestedSize":2,
                    "constraints":[]
                  }
                }}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_FIELD_CONFLICT_PARTIAL_UNRESOLVED_ECHO");
    }

    @Test
    void decodesStrictObjectCarrierAndIgnoresNullSiblingFields() throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        JsonNode compiled = compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL",
                 "goal":"{\\\"goalKind\\\":\\\"PORTFOLIO_RECOMMEND\\\",\\\"requestedSize\\\":2,\\\"constraints\\\":[]}",
                 "candidateKey":null,"clarification":null,"recentReference":null}
                """));

        assertThat(compiled.path("kind").textValue()).isEqualTo("SEMANTIC_ROUTE");
        assertThat(compiled.path("goal").path("goalKind").textValue())
                .isEqualTo("PORTFOLIO_RECOMMEND");
    }

    @Test
    void rejectsMalformedOrNonObjectCarrierWithoutSemanticRepair() throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "给我推荐两个项目"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":"{} trailing"}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_CARRIER_UNPARSEABLE_GOAL");
        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":"[]"}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_CARRIER_NOT_OBJECT_GOAL");
    }

    @Test
    void allSixGoalKindsCompileThroughCanonicalCodecWithoutSemanticRepair()
            throws Exception {
        assertCanonical("介绍 SQL 审计项目", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_FACT",
                  "subjects":[{"kind":"PROJECT","reference":"sql-audit",
                    "inputText":"SQL 审计项目"}],
                  "facets":["OVERVIEW"],"depth":"STANDARD"}}
                """);
        assertCanonical("比较 SQL 审计项目和 Agent 能力集成 MVP", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_COMPARE",
                  "subjects":[
                    {"kind":"PROJECT","reference":"sql-audit","inputText":"SQL 审计项目"},
                    {"kind":"PROJECT","reference":"agent-capability-mvp",
                     "inputText":"Agent 能力集成 MVP"}],
                  "dimensions":["ARCHITECTURE"]}}
                """);
        assertCanonical("给我推荐两个项目", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_RECOMMEND",
                  "requestedSize":2,"constraints":[]}}
                """);
        assertCanonical("解释幂等", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"GENERAL_EXPLANATION",
                  "topicText":"幂等","depth":"STANDARD"}}
                """);
        assertCanonical("比较 Redis 和 Memcached", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"GENERAL_COMPARISON",
                  "subjectTexts":["Redis","Memcached"],"dimensions":["MECHANISM"]}}
                """);
        JsonNode applied = assertCanonical("用幂等分析 SQL 审计项目的验证", """
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"APPLY_GENERAL_CONCEPT_TO_PORTFOLIO",
                  "subjects":[{"kind":"PROJECT","reference":"sql-audit",
                    "inputText":"SQL 审计项目"}],
                  "conceptText":"幂等","portfolioFacet":"VERIFICATION",
                  "depth":"STANDARD"}}
                """);
        assertThat(applied.path("goal").path("knowledgeRequirement").textValue())
                .isEqualTo("PUBLIC_PORTFOLIO_EVIDENCE");
    }

    @Test
    void rejectsOpenTextGeneralComparisonDimensionBeforeCanonicalCodec()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "比较 Redis 和 Memcached"));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree("""
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"GENERAL_COMPARISON",
                  "subjectTexts":["Redis","Memcached"],"dimensions":["机制"]}}
                """)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE");
    }

    @Test
    void partialComparisonPreservesOneKnownSubjectWhileClarifyingTheOther()
            throws Exception {
        GoalProviderDraftCompiler compiler = new GoalProviderDraftCompiler(input(
                "比较 SQL 审计项目和另一个项目"));

        JsonNode canonical = compiler.compile(mapper.readTree("""
                {"decision":"NEEDS_CLARIFICATION","clarification":{
                  "field":"SUBJECT","prompt":"还要比较哪个公开项目？","goal":{
                    "goalKind":"PORTFOLIO_COMPARE",
                    "subjects":[{"kind":"PROJECT","reference":"sql-audit",
                      "inputText":"SQL 审计项目"}],
                    "dimensions":["ARCHITECTURE"]}}}
                """));

        JsonNode blocked = canonical.path("clarification").path("blockedGoal");
        assertThat(blocked.path("subjects")).hasSize(1);
        assertThat(blocked.path("subjects").get(0).path("reference").textValue())
                .isEqualTo("sql-audit");
        new GoalProposalCodec().decode(canonical.toString(), input(
                "比较 SQL 审计项目和另一个项目"));
    }

    private JsonNode assertCanonical(String text, String draft) throws Exception {
        GoalInterpretationInput interpretationInput = input(text);
        JsonNode canonical = new GoalProviderDraftCompiler(interpretationInput)
                .compile(mapper.readTree(draft));
        new GoalProposalCodec().decode(canonical.toString(), interpretationInput);
        return canonical;
    }

    private GoalInterpretationInput input(String text) {
        return new GoalInterpretationInput(
                text, List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit", "SQL 审计项目"),
                        new GoalInterpretationInput.PublicSubjectDescriptor(
                                GoalSubjectReference.Kind.PROJECT,
                                "agent-capability-mvp", "Agent 能力集成 MVP")),
                Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, List.of(),
                Set.of(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal.Route
                                .STANDARD_GOAL,
                        com.portfolio.agent.turn.planning.SemanticRouteProposal.Route
                                .NEEDS_CLARIFICATION),
                Set.of("CAPABILITY_SQL"));
    }
}
