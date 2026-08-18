package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalProposalCodecTest {

    private final GoalProposalCodec codec = new GoalProposalCodec();

    @Test
    void decodesClosedUserGoalsWithoutTaskOrDagAuthority() {
        GoalInterpretationInput input = input("请介绍 SQL 审计项目，并解释幂等");
        GoalInterpretationResult result = codec.decode("""
                {
                  "kind":"GOALS",
                  "goals":[
                    {
                      "goalKey":"portfolio-overview",
                      "goalKind":"PORTFOLIO_FACT",
                      "inputAnchor":{"text":"介绍 SQL 审计项目","start":1},
                      "subjectCandidates":[{
                        "kind":"PROJECT","reference":"sql-audit","basis":"EXPLICIT_INPUT",
                        "anchor":{"text":"SQL 审计项目","start":4}
                      }],
                      "requestedOutputs":["OVERVIEW"],
                      "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                      "parameters":{"kind":"PORTFOLIO_FACT","facets":["BACKGROUND","STATUS"]}
                    },
                    {
                      "goalKey":"general-idempotency",
                      "goalKind":"GENERAL_EXPLANATION",
                      "inputAnchor":{"text":"解释幂等","start":14},
                      "subjectCandidates":[],
                      "requestedOutputs":["EXPLANATION"],
                      "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                      "parameters":{"kind":"GENERAL_EXPLANATION",
                        "topicAnchor":{"text":"幂等","start":16},"depth":"STANDARD"}
                    }
                  ]
                }
                """, input);

        assertThat(result.getKind()).isEqualTo(GoalInterpretationResult.Kind.GOALS);
        assertThat(result.getGoalProposal().orElseThrow().getGoals()).hasSize(2);
        UserGoalProposal.ProposedGoal first = result.getGoalProposal().orElseThrow().getGoals().get(0);
        assertThat(first.getGoalKind()).isEqualTo(GoalKind.PORTFOLIO_FACT);
        assertThat(first.getParameters()).isInstanceOf(UserGoalProposal.PortfolioFactParameters.class);
        assertThat(first.getSubjectCandidates()).extracting(GoalSubjectReference::getReference)
                .containsExactly("sql-audit");
    }

    @Test
    void decodesClarificationAndConversationalResultsWithoutFakeGoals() {
        GoalInterpretationResult clarification = codec.decode("""
                {"kind":"CLARIFICATION","clarification":{"field":"SUBJECT",
                 "prompt":"请选择要了解的公开项目","inputAnchor":{"text":"这个项目","start":0}}}
                """, input("这个项目怎么样"));
        GoalInterpretationResult conversational = codec.decode("""
                {"kind":"CONVERSATIONAL","message":"你好，我可以介绍公开项目。"}
                """, input("你好"));

        assertThat(clarification.getKind()).isEqualTo(GoalInterpretationResult.Kind.CLARIFICATION);
        assertThat(clarification.getClarification().orElseThrow().getField())
                .isEqualTo(ClarificationProposal.Field.SUBJECT);
        assertThat(conversational.getKind()).isEqualTo(GoalInterpretationResult.Kind.CONVERSATIONAL);
        assertThat(conversational.getMessage().orElseThrow()).contains("公开项目");
    }

    @Test
    void rejectsTaskDagAndUnknownFieldInjection() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"GOALS","goals":[{
                  "goalKey":"g1","goalKind":"GENERAL_EXPLANATION",
                  "inputAnchor":{"text":"解释幂等","start":0},
                  "subjectCandidates":[],"requestedOutputs":["EXPLANATION"],
                  "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                  "taskType":"GENERAL_EXPLANATION",
                  "parameters":{"kind":"GENERAL_EXPLANATION",
                    "topicAnchor":{"text":"幂等","start":2},"depth":"STANDARD"}
                }],"dependencies":[]}
                """, input("解释幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field");
    }

    @Test
    void rejectsDuplicateKeysAndDamagedJsonWithoutRepair() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"CONVERSATIONAL","kind":"GOALS","goals":[]}
                """, input("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid goal proposal JSON");
        assertThatThrownBy(() -> codec.decode("{kind:GOALS", input("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid goal proposal JSON");
    }

    @Test
    void rejectsAnchorThatDoesNotMatchOriginalInput() {
        assertThatThrownBy(() -> codec.decode("""
                {"kind":"GOALS","goals":[{
                  "goalKey":"g1","goalKind":"GENERAL_EXPLANATION",
                  "inputAnchor":{"text":"不存在的原文","start":0},
                  "subjectCandidates":[],"requestedOutputs":["EXPLANATION"],
                  "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                  "parameters":{"kind":"GENERAL_EXPLANATION",
                    "topicAnchor":{"text":"幂等","start":2},"depth":"STANDARD"}
                }]}
                """, input("解释幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchor");
    }

    @Test
    void rejectsMoreThanSixGoalsAndOversizedProviderOutput() {
        String repeated = """
                {"goalKey":"g%s","goalKind":"GENERAL_EXPLANATION",
                 "inputAnchor":{"text":"幂等","start":0},"subjectCandidates":[],
                 "requestedOutputs":["EXPLANATION"],
                 "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                 "parameters":{"kind":"GENERAL_EXPLANATION",
                   "topicAnchor":{"text":"幂等","start":0},"depth":"STANDARD"}}
                """;
        String goals = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(index -> repeated.formatted(index))
                .collect(java.util.stream.Collectors.joining(","));
        assertThatThrownBy(() -> codec.decode(
                "{\"kind\":\"GOALS\",\"goals\":[" + goals + "]}", input("幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between one and six");

        assertThatThrownBy(() -> codec.decode(" ".repeat(30000), input("幂等")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounded");
    }

    private GoalInterpretationInput input(String text) {
        return new GoalInterpretationInput(
                text, List.of(), List.of(
                        new GoalInterpretationInput.PublicSubjectDescriptor(
                                GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                java.util.Set.of(GoalKind.values()));
    }
}
