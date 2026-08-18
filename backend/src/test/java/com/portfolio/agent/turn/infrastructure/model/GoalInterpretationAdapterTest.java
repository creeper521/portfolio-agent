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
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(request -> {
            captured.set(request);
            return new StructuredModelResponse("""
                    {"kind":"GOALS","goals":[{
                      "goalKey":"general-goal","goalKind":"GENERAL_EXPLANATION",
                      "inputAnchor":{"text":"解释幂等","start":0},
                      "subjectCandidates":[],"requestedOutputs":["EXPLANATION"],
                      "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                      "parameters":{"kind":"GENERAL_EXPLANATION",
                        "topicAnchor":{"text":"幂等","start":2},"depth":"STANDARD"}
                    }]}
                    """);
        }, new ObjectMapper(), new GoalProposalCodec(), 1200,
                Duration.ofSeconds(2), Clock.systemUTC());

        GoalInterpretationResult result = adapter.interpret(input());
        assertThat(result.getKind()).isEqualTo(GoalInterpretationResult.Kind.GOALS);
        assertThat(captured.get().userPrompt()).contains("allowedGoalKinds", "publicSubjects")
                .doesNotContain("taskType", "dependencies");
    }

    private GoalInterpretationInput input() {
        return new GoalInterpretationInput(
                "解释幂等", List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }
}
