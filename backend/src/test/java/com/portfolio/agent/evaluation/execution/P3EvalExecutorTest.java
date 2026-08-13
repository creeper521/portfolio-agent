package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.routing.adapter.execution.P3PortfolioSemanticTaskExecutor;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
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

class P3EvalExecutorTest {

    @Test
    void consumesTypedTaskAndMapsOnlySafeOutcomeMetadata() {
        P3PortfolioSemanticTaskExecutor p3 = mock(P3PortfolioSemanticTaskExecutor.class);
        RuntimeContentSnapshot bundle = mock(RuntimeContentSnapshot.class);
        when(bundle.getContentVersion()).thenReturn("public-v1");
        when(p3.execute(any())).thenReturn(TaskOutcome.capabilityUnavailable(
                "eval-task", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "CAPABILITY_TEMPORARILY_UNAVAILABLE"));
        P3EvalExecutor executor = new P3EvalExecutor(p3, bundle);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(), EvalLayer.INTELLIGENCE, 1, task()),
                new EvalRunContext("run-1", "public-v1"));

        assertThat(observation.getStatus()).isEqualTo(
                com.portfolio.agent.evaluation.domain.EvalObservationStatus.FAIL);
        assertThat(observation.getReasonCodes())
                .containsExactly("CAPABILITY_TEMPORARILY_UNAVAILABLE");
        assertThat(observation.getSelectedClaimIds()).isEmpty();
        assertThat(observation.getSelectedEvidenceIds()).isEmpty();
        verify(p3).execute(any());
    }

    @Test
    void missingTypedTaskFailsClosedWithoutCallingP3() {
        P3PortfolioSemanticTaskExecutor p3 = mock(P3PortfolioSemanticTaskExecutor.class);
        RuntimeContentSnapshot bundle = mock(RuntimeContentSnapshot.class);
        when(bundle.getContentVersion()).thenReturn("public-v1");
        P3EvalExecutor executor = new P3EvalExecutor(p3, bundle);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput("case-1", List.of(), EvalLayer.INTELLIGENCE, 1),
                new EvalRunContext("run-1", "public-v1"));

        assertThat(observation.getStatus())
                .isEqualTo(com.portfolio.agent.evaluation.domain.EvalObservationStatus.ERROR);
        assertThat(observation.getReasonCodes()).containsExactly("MISSING_TYPED_TASK");
        org.mockito.Mockito.verifyNoInteractions(p3);
    }

    private static SemanticTask task() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        return SemanticTask.create(
                "eval-task", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "describe project",
                new SemanticTaskParameters.PortfolioFact(
                        subject, Set.of("OVERVIEW"), "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
    }
}
