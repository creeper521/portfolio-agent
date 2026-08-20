package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GoalInterpretationAdapterTest {
    @Test void sendsOnlyGoalLevelAuthorityAndDecodesStrictProposal() {
        AtomicReference<StructuredModelRequest> captured = new AtomicReference<>();
        String systemPrompt = "goal-system-prompt";
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(request -> {
            captured.set(request);
            return new StructuredModelResponse("""
                    {
                      "kind":"SEMANTIC_ROUTE",
                      "route":"STANDARD_GOAL",
                      "candidateKey":null,
                      "goal":{
                        "goalKey":"general-goal",
                        "goalKind":"GENERAL_EXPLANATION",
                        "inputAnchor":{"text":"解释幂等","start":0},
                        "subjectCandidates":[],
                        "requestedOutputs":["EXPLANATION"],
                        "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                        "parameters":{"kind":"GENERAL_EXPLANATION",
                          "topicAnchor":{"text":"幂等","start":2},"depth":"STANDARD"}
                      },
                      "clarification":null
                    }
                    """);
        }, new ObjectMapper(), new GoalProposalCodec(), systemPrompt, 1200,
                Duration.ofSeconds(2));

        GoalInterpretationResult result = adapter.interpret(
                input(), com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(3), Clock.systemUTC()));
        assertThat(result.getKind())
                .isEqualTo(GoalInterpretationResult.Kind.SEMANTIC_ROUTE);
        assertThat(captured.get().systemPrompt()).isEqualTo(systemPrompt);
        assertThat(captured.get().userPrompt()).contains(
                        "interpretationMode", "discussionState",
                        "allowedRoutes", "routeCandidates",
                        "allowedGoalKinds", "publicSubjects")
                .doesNotContain("taskType", "dependencies");
        assertThat(captured.get().maxOutputTokens()).isEqualTo(1200);
        assertThat(captured.get().temperature()).isZero();
    }

    private GoalInterpretationInput input() {
        return new GoalInterpretationInput(
                "解释幂等", List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }
}
