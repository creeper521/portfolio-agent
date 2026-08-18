package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioEvalExecutorTest {

    @Test
    void consumesTypedTaskAndMapsOnlySafeOutcomeMetadata() {
        PortfolioTaskExecutor p3 = mock(PortfolioTaskExecutor.class);
        RuntimeContentSnapshot bundle = mock(RuntimeContentSnapshot.class);
        when(bundle.getContentVersion()).thenReturn("public-v1");
        when(p3.execute(any())).thenThrow(new com.portfolio.agent.turn.execution.TaskTerminalException(
                com.portfolio.agent.turn.execution.TaskTerminalException.Kind.FAILED,
                com.portfolio.agent.turn.execution.TaskTerminalReason.CAPABILITY_UNAVAILABLE));
        PortfolioEvalExecutor executor = new PortfolioEvalExecutor(p3, bundle);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(), EvalLayer.INTELLIGENCE, 1, task()),
                new EvalRunContext("run-1", "public-v1"));

        assertThat(observation.getStatus()).isEqualTo(
                com.portfolio.agent.evaluation.domain.EvalObservationStatus.FAIL);
        assertThat(observation.getReasonCodes())
                .containsExactly("CAPABILITY_UNAVAILABLE");
        assertThat(observation.getSelectedClaimIds()).isEmpty();
        assertThat(observation.getSelectedEvidenceIds()).isEmpty();
        verify(p3).execute(any());
    }

    @Test
    void missingTypedTaskFailsClosedWithoutCallingP3() {
        PortfolioTaskExecutor p3 = mock(PortfolioTaskExecutor.class);
        RuntimeContentSnapshot bundle = mock(RuntimeContentSnapshot.class);
        when(bundle.getContentVersion()).thenReturn("public-v1");
        PortfolioEvalExecutor executor = new PortfolioEvalExecutor(p3, bundle);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(), EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "public-v1"));

        assertThat(observation.getStatus())
                .isEqualTo(com.portfolio.agent.evaluation.domain.EvalObservationStatus.ERROR);
        assertThat(observation.getReasonCodes()).containsExactly("MISSING_TYPED_TASK");
        org.mockito.Mockito.verifyNoInteractions(p3);
    }

    private static SemanticTask task() {
        GoalSubjectReference subject = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, "project-a",
                GoalSubjectReference.Basis.CONTINUATION, null);
        UserGoalProposal.PortfolioFactParameters parameters =
                new UserGoalProposal.PortfolioFactParameters(Set.of(UserGoalProposal.Facet.OVERVIEW));
        return SemanticTask.of(
                "eval-task", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT, parameters, List.of(subject)),
                Set.of(GoalRequestedOutput.OVERVIEW));
    }
}

