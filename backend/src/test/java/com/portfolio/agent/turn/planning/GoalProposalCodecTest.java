package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalProposalCodecTest {

    private final GoalProposalCodec codec = new GoalProposalCodec();

    @Test
    void rejectsTrailingJsonValue() {
        assertThatThrownBy(() -> codec.decode(
                standardPortfolioRoute("sql-audit") + " {}",
                input("介绍 SQL 审计项目")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid goal proposal JSON");
    }

    @Test
    void decodesAClosedStandardSemanticRoute() {
        GoalInterpretationResult result = codec.decode(standardPortfolioRoute(
                "sql-audit"), input("介绍 SQL 审计项目"));

        assertThat(result.getKind())
                .isEqualTo(GoalInterpretationResult.Kind.SEMANTIC_ROUTE);
        SemanticRouteProposal route =
                result.getRouteProposal().orElseThrow();
        assertThat(route.getRoute())
                .isEqualTo(SemanticRouteProposal.Route.STANDARD_GOAL);
        assertThat(route.getGoalProposal().orElseThrow().getGoals())
                .singleElement()
                .extracting(UserGoalProposal.ProposedGoal::getGoalKind)
                .isEqualTo(GoalKind.PORTFOLIO_FACT);
    }

    @Test
    void decodesOnlyAnExactTypedRecentGoalReference() {
        String validProposal = """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":{"goalId":"goal-1","sectionId":null},
                  "goal":{
                    "goalKey":"expand-recent",
                    "goalKind":"PORTFOLIO_FACT",
                    "inputAnchor":{"text":"进一步展开","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["SOLUTION"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{"kind":"PORTFOLIO_FACT","facets":["SOLUTION"],
                      "depth":"DETAILED"}
                  },
                  "clarification":null
                }
                """;
        GoalInterpretationResult result = codec.decode(validProposal, recentStateInput());

        assertThat(result.getRouteProposal().orElseThrow().getRecentReference())
                .contains(new SemanticRouteProposal.RecentSemanticReference(
                        "goal-1", null));
        assertThatThrownBy(() -> codec.decode(
                validProposal
                        .replace("\"sectionId\":null",
                                "\"sectionId\":\"section-goal-1-1\"")
                        .replace("SOLUTION", "STATUS"),
                recentStateInput()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match requested facet");
        assertThatThrownBy(() -> codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":{"goalId":"invented-goal","sectionId":null},
                  "goal":{
                    "goalKey":"expand-recent",
                    "goalKind":"PORTFOLIO_FACT",
                    "inputAnchor":{"text":"进一步展开","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["SOLUTION"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{"kind":"PORTFOLIO_FACT","facets":["SOLUTION"],
                      "depth":"DETAILED"}
                  },
                  "clarification":null
                }
                """, recentStateInput()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside typed recent state");
    }

    @Test
    void decodesAndPreservesAllGeneralExplanationDepths() {
        for (UserGoalProposal.Depth expected :
                UserGoalProposal.Depth.values()) {
            GoalInterpretationResult result = codec.decode("""
                    {
                      "kind":"SEMANTIC_ROUTE",
                      "route":"STANDARD_GOAL",
                      "candidateKey":null,
                      "recentReference":null,
                      "goal":{
                        "goalKey":"general-goal",
                        "goalKind":"GENERAL_EXPLANATION",
                        "inputAnchor":{"text":"解释幂等","start":0},
                        "subjectCandidates":[],
                        "requestedOutputs":["EXPLANATION"],
                        "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                        "parameters":{
                          "kind":"GENERAL_EXPLANATION",
                          "topicAnchor":{"text":"幂等","start":2},
                          "depth":"%s"
                        }
                      },
                      "clarification":null
                    }
                    """.formatted(expected.name()), input("解释幂等"));

            UserGoalProposal.GeneralExplanationParameters parameters =
                    (UserGoalProposal.GeneralExplanationParameters) result
                            .getRouteProposal().orElseThrow()
                            .getGoalProposal().orElseThrow()
                            .getGoals().getFirst().getParameters();
            assertThat(parameters.getDepth()).isEqualTo(expected);
        }
    }

    @Test
    void portfolioFactDepthAndComparisonDimensionsAreClosedTypedIntent() {
        GoalInterpretationResult fact = codec.decode(standardPortfolioRoute(
                "sql-audit", "DETAILED"), input("介绍 SQL 审计项目"));
        UserGoalProposal.PortfolioFactParameters factParameters =
                (UserGoalProposal.PortfolioFactParameters) fact.getRouteProposal()
                        .orElseThrow().getGoalProposal().orElseThrow()
                        .getGoals().getFirst().getParameters();

        assertThat(factParameters.getDepth()).isEqualTo(UserGoalProposal.Depth.DETAILED);
        assertThatThrownBy(() -> codec.decode(standardComparisonRoute("INVENTED"),
                inputWithTwoSubjects("比较 SQL 审计项目和 Agent 项目")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void recommendationConstraintsMustComeFromTrustedPublicCatalog() {
        String proposal = """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"recommend-projects",
                    "goalKind":"PORTFOLIO_RECOMMEND",
                    "inputAnchor":{"text":"推荐后端项目","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["RECOMMENDATION"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{"kind":"PORTFOLIO_RECOMMEND","requestedSize":2,
                      "constraints":["%s"]}
                  },
                  "clarification":null
                }
                """;

        GoalInterpretationResult accepted = codec.decode(
                proposal.formatted("CAREER_TRACK_JAVA_BACKEND"),
                recommendationInput());
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) accepted
                        .getRouteProposal().orElseThrow().getGoalProposal().orElseThrow()
                        .getGoals().getFirst().getParameters();
        assertThat(parameters.getConstraints())
                .containsExactly("CAREER_TRACK_JAVA_BACKEND");
        assertThatThrownBy(() -> codec.decode(
                proposal.formatted("CAPABILITY_INVENTED"), recommendationInput()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the public catalog");
    }

    @Test
    void decodesClosedClarificationAndConversationalResults() {
        GoalInterpretationResult clarification = codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"NEEDS_CLARIFICATION",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":null,
                  "clarification":{
                    "field":"SUBJECT",
                    "prompt":"请选择要了解的公开项目",
                    "blockedGoal":{
                      "goalKind":"PORTFOLIO_FACT",
                      "subjects":[],
                      "requestedOutputs":["OVERVIEW"],
                      "facets":["OVERVIEW"],
                      "dimensions":[],
                      "requestedSize":null,
                      "constraints":[],
                      "portfolioDepth":"DETAILED",
                      "unresolvedField":"SUBJECT",
                      "askedFields":["SUBJECT"],
                      "remainingFields":[],
                      "depth":1
                    }
                  }
                }
                """, input("这个项目怎么样"));
        GoalInterpretationResult conversational = codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你好，我可以介绍公开项目。"}
                """, input("你好"));

        assertThat(clarification.getRouteProposal().orElseThrow()
                .getClarification().orElseThrow().getField())
                .isEqualTo(ClarificationProposal.Field.SUBJECT);
        assertThat(clarification.getRouteProposal().orElseThrow()
                .getClarification().orElseThrow().getBlockedGoal().getPortfolioDepth())
                .isEqualTo(UserGoalProposal.Depth.DETAILED);
        assertThat(conversational.getKind())
                .isEqualTo(GoalInterpretationResult.Kind.CONVERSATIONAL);
    }

    @Test
    void rejectsConversationalMessagesOutsideRuntimeLanguageAndReplayBoundary() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","message":"This is a complete English response."}
                """, input("聊聊你能做什么")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primarily Chinese");

        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你好，API SDK HTTP JSON English technical identifiers are all copied here."}
                """, input("聊聊你能做什么")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primarily Chinese");

        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你好，Voici une réponse principalement rédigée en français."}
                """, input("聊聊你能做什么")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primarily Chinese");

        String overlong = "这是一条过长的社交回复。".repeat(15);
        assertThatThrownBy(() -> codec.decode(
                "{\"kind\":\"CONVERSATIONAL\",\"message\":\""
                        + overlong + "\"}", input("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounded");

        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你刚才说想知道这个网站具体能够帮助你做什么，我可以回答。"}
                """, input("想知道这个网站具体能够帮助你做什么")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeats visitor input");

        GoalInterpretationResult accepted = codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你好，我可以介绍公开项目，也可以回答技术问题。"}
                """, input("你好"));
        assertThat(accepted.getKind())
                .isEqualTo(GoalInterpretationResult.Kind.CONVERSATIONAL);
    }

    @Test
    void decodesOnlyCandidateKeysAndNeverStateHandles() {
        GoalInterpretationResult result = codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"ENTER_RECOMMENDED_RESULT",
                  "candidateKey":"C1",
                  "recentReference":null,
                  "goal":null,
                  "clarification":null
                }
                """, candidateInput());

        assertThat(result.getRouteProposal().orElseThrow()
                .getCandidateKey()).contains("C1");
        assertThatThrownBy(() -> codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"ENTER_RECOMMENDED_RESULT",
                  "candidateKey":"C1",
                  "recentReference":null,
                  "goal":null,
                  "clarification":null,
                  "contextHandle":"secret-handle"
                }
                """, candidateInput()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field");
    }

    @Test
    void candidateClarificationUsesBackendOwnedSelectionTemplate() {
        String proposal = """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"NEEDS_CLARIFICATION",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":null,
                  "clarification":null
                }
                """;

        GoalInterpretationResult result = codec.decode(
                proposal, candidateInput());
        assertThat(result.getRouteProposal().orElseThrow()
                .getClarification()).isEmpty();
        assertThatThrownBy(() -> codec.decode(
                proposal, input("这个项目怎么样")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clarification is required");
    }

    @Test
    void decodesDiscussionRouteWithoutAllowingTheModelToNameTheLockedSubject() {
        GoalInterpretationResult result = codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"CONTINUE_CURRENT_PROJECT",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"discussion-goal",
                    "goalKind":"PORTFOLIO_FACT",
                    "inputAnchor":{"text":"介绍实现","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["SOLUTION"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{
                      "kind":"PORTFOLIO_FACT",
                      "facets":["SOLUTION"],
                      "depth":"STANDARD"
                    }
                  },
                  "clarification":null
                }
                """, discussionInput());

        assertThat(result.getRouteProposal().orElseThrow().getRoute())
                .isEqualTo(
                        SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT);
        assertThat(result.getRouteProposal().orElseThrow()
                .getGoalProposal().orElseThrow().getGoals().getFirst()
                .getSubjectCandidates()).isEmpty();
    }

    @Test
    void rejectsRetiredGoalsAndClarificationRootShapes() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"GOALS","goals":[]}
                """, input("解释幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CLARIFICATION","clarification":{}}
                """, input("这个项目")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void rejectsInventedPublicSubjectAndMismatchedAnchor() {
        assertThatThrownBy(() -> codec.decode(
                standardPortfolioRoute("invented-project"),
                input("介绍 SQL 审计项目")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public subject");
        assertThatThrownBy(() -> codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"general-goal",
                    "goalKind":"GENERAL_EXPLANATION",
                    "inputAnchor":{"text":"不存在","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["EXPLANATION"],
                    "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                    "parameters":{
                      "kind":"GENERAL_EXPLANATION",
                      "topicAnchor":{"text":"幂等","start":2},
                      "depth":"STANDARD"
                    }
                  },
                  "clarification":null
                }
                """, input("解释幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchor");
    }

    @Test
    void rejectsProviderOwnedSubjectBasisAndRequiresAnExplicitAnchor() {
        for (String backendBasis : List.of("SURFACE_HINT", "CONTINUATION", "RECENT_TURN")) {
            assertThatThrownBy(() -> codec.decode(
                    standardPortfolioRoute("sql-audit")
                            .replace("EXPLICIT_INPUT", backendBasis),
                    input("介绍 SQL 审计项目")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-public subject");
        }
        assertThatThrownBy(() -> codec.decode(
                standardPortfolioRoute("sql-audit")
                        .replace("\"anchor\":{\"text\":\"SQL 审计项目\",\"start\":3}",
                                "\"anchor\":null"),
                input("介绍 SQL 审计项目")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchor");
    }

    @Test
    void normalizesWrongStartOnlyWhenAnchorTextIsUnique() {
        GoalInterpretationResult result = codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"general-goal",
                    "goalKind":"GENERAL_EXPLANATION",
                    "inputAnchor":{"text":"解释 Redis 的持久化机制","start":1},
                    "subjectCandidates":[],
                    "requestedOutputs":["EXPLANATION"],
                    "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                    "parameters":{
                      "kind":"GENERAL_EXPLANATION",
                      "topicAnchor":{"text":"Redis 的持久化机制","start":0},
                      "depth":"STANDARD"
                    }
                  },
                  "clarification":null
                }
                """, input("解释 Redis 的持久化机制"));

        UserGoalProposal.ProposedGoal goal = result.getRouteProposal()
                .orElseThrow().getGoalProposal().orElseThrow()
                .getGoals().getFirst();
        assertThat(goal.getInputAnchor().getStart()).isZero();
        assertThat(((UserGoalProposal.GeneralExplanationParameters)
                goal.getParameters()).getTopicAnchor().getStart()).isEqualTo(3);

        assertThatThrownBy(() -> codec.decode("""
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"general-goal",
                    "goalKind":"GENERAL_EXPLANATION",
                    "inputAnchor":{"text":"解释幂等与幂等","start":0},
                    "subjectCandidates":[],
                    "requestedOutputs":["EXPLANATION"],
                    "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                    "parameters":{
                      "kind":"GENERAL_EXPLANATION",
                      "topicAnchor":{"text":"幂等","start":1},
                      "depth":"STANDARD"
                    }
                  },
                  "clarification":null
                }
                """, input("解释幂等与幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchor");
    }

    @Test
    void rejectsDuplicateKeysDamagedJsonAndOversizedOutput() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","kind":"SEMANTIC_ROUTE"}
                """, input("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid goal proposal JSON");
        assertThatThrownBy(() -> codec.decode(
                "{kind:SEMANTIC_ROUTE", input("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid goal proposal JSON");
        assertThatThrownBy(() -> codec.decode(
                " ".repeat(30000), input("幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounded");
    }

    private String standardPortfolioRoute(String reference) {
        return standardPortfolioRoute(reference, "STANDARD");
    }

    private String standardPortfolioRoute(String reference, String depth) {
        return """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"portfolio-overview",
                    "goalKind":"PORTFOLIO_FACT",
                    "inputAnchor":{"text":"介绍 SQL 审计项目","start":0},
                    "subjectCandidates":[{
                      "kind":"PROJECT",
                      "reference":"%s",
                      "basis":"EXPLICIT_INPUT",
                      "anchor":{"text":"SQL 审计项目","start":3}
                    }],
                    "requestedOutputs":["OVERVIEW"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{
                      "kind":"PORTFOLIO_FACT",
                      "facets":["OVERVIEW"],
                      "depth":"%s"
                    }
                  },
                  "clarification":null
                }
                """.formatted(reference, depth);
    }

    private String standardComparisonRoute(String dimension) {
        return """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"STANDARD_GOAL",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":{
                    "goalKey":"portfolio-comparison",
                    "goalKind":"PORTFOLIO_COMPARE",
                    "inputAnchor":{"text":"比较 SQL 审计项目和 Agent 项目","start":0},
                    "subjectCandidates":[
                      {"kind":"PROJECT","reference":"sql-audit","basis":"EXPLICIT_INPUT",
                       "anchor":{"text":"SQL 审计项目","start":3}},
                      {"kind":"PROJECT","reference":"agent","basis":"EXPLICIT_INPUT",
                       "anchor":{"text":"Agent 项目","start":12}}
                    ],
                    "requestedOutputs":["COMPARISON"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{"kind":"PORTFOLIO_COMPARE","dimensions":["%s"]}
                  },
                  "clarification":null
                }
                """.formatted(dimension);
    }

    private GoalInterpretationInput input(String text) {
        return new GoalInterpretationInput(
                text,
                List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }

    private GoalInterpretationInput inputWithTwoSubjects(String text) {
        return new GoalInterpretationInput(
                text, List.of(),
                List.of(
                        new GoalInterpretationInput.PublicSubjectDescriptor(
                                GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目"),
                        new GoalInterpretationInput.PublicSubjectDescriptor(
                                GoalSubjectReference.Kind.PROJECT, "agent", "Agent 项目")),
                Set.of(GoalKind.values()));
    }

    private GoalInterpretationInput recentStateInput() {
        GoalInterpretationInput.PublicSubjectDescriptor subject =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit", "SQL 审计项目");
        ConversationSemanticState state = new ConversationSemanticState(
                "public-1", List.of(new ConversationSemanticState.GoalSummary(
                "goal-1", GoalKind.PORTFOLIO_FACT,
                List.of(new ConversationSemanticState.Subject(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit")),
                Set.of(GoalRequestedOutput.SOLUTION),
                Set.of(UserGoalProposal.Facet.SOLUTION),
                UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(),
                List.of(new ConversationSemanticState.SectionReference(
                        "section-goal-1-1",
                        com.portfolio.agent.turn.execution.AnswerSectionType.SOLUTION)))),
                Instant.parse("2026-08-24T05:00:00Z"));
        return new GoalInterpretationInput(
                "进一步展开", List.of(), List.of(subject),
                Set.of(GoalKind.PORTFOLIO_FACT),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE, null, List.of(),
                Set.of(SemanticRouteProposal.Route.STANDARD_GOAL), Set.of(), null,
                SemanticTaskParameters.AudienceProfile.GUEST, state);
    }

    private GoalInterpretationInput recommendationInput() {
        return new GoalInterpretationInput(
                "推荐后端项目", List.of(), List.of(), Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE, null, List.of(),
                Set.of(SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                Set.of("CAREER_TRACK_JAVA_BACKEND", "CAPABILITY_SQL"));
    }

    private GoalInterpretationInput candidateInput() {
        return new GoalInterpretationInput(
                "继续第二个",
                List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目")),
                Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null,
                List.of(new GoalInterpretationInput.RouteCandidate(
                        "C1",
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目",
                        Set.of("SQL 审计项目"))),
                Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.ENTER_RECOMMENDED_RESULT,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION));
    }

    private GoalInterpretationInput discussionInput() {
        GoalInterpretationInput.PublicSubjectDescriptor locked =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目");
        return new GoalInterpretationInput(
                "介绍实现",
                List.of(),
                List.of(locked),
                Set.of(
                        GoalKind.PORTFOLIO_FACT,
                        GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO),
                GoalInterpretationInput.InterpretationMode.DISCUSSION,
                GoalInterpretationInput.DiscussionState.ACTIVE,
                locked,
                List.of(),
                Set.of(
                        SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT,
                        SemanticRouteProposal.Route.START_NEW_TOPIC,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION));
    }
}
