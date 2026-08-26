package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralTaskExecutorTest {
    @Test void executesTypedRequestToResultPresentationAndArtifact() {
        AtomicReference<GeneralKnowledgeRequest> captured = new AtomicReference<>();
        AtomicReference<ResolvedModelExecution> capturedExecution =
                new AtomicReference<>();
        GeneralTaskExecutor executor = new GeneralTaskExecutor(
                GeneralTestFixtures.generator((request, modelExecution) -> {
                    captured.set(request);
                    capturedExecution.set(modelExecution);
                    return StructuredModelTestFixtures.validatedGeneral(
                            GeneralTestFixtures.VALID_EXPLANATION);
                }),
                new GeneralPresentationComposer());
        TaskExecutionContext context = mock(TaskExecutionContext.class);
        UserGoalProposal.GeneralExplanationParameters parameters =
                new UserGoalProposal.GeneralExplanationParameters(
                        new UserGoalProposal.InputAnchor("并发控制", 0), UserGoalProposal.Depth.STANDARD);
        when(context.getTask()).thenReturn(SemanticTask.of(
                "task-general", SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(
                        GoalKind.GENERAL_EXPLANATION, parameters, List.of(),
                        SemanticTaskParameters.AudienceProfile.INTERVIEWER)));
        when(context.getContentReleaseId()).thenReturn("public-1");
        when(context.getDeadline()).thenReturn(GeneralTestFixtures.explanation().getDeadline());
        when(context.getCancellation()).thenReturn(new CancellationSignal());
        ResolvedModelExecution selected = ResolvedModelExecution.none();
        when(context.getModelExecution()).thenReturn(selected);

        TaskExecutionResult execution = executor.execute(context);
        assertThat(execution.getFulfillment()).isEqualTo(TaskOutcome.Fulfillment.FULL);
        assertThat(execution.getArtifact().getSemanticResult()).isInstanceOf(GeneralSemanticResult.class);
        assertThat(execution.getArtifact().getPresentation()).isInstanceOf(GeneralPresentation.class);
        assertThat(execution.getArtifact().getProvenance().getPublicSourceKeys()).isEmpty();
        assertThat(captured.get().getAudience())
                .isEqualTo(GeneralKnowledgeRequest.Audience.INTERVIEWER);
        assertThat(capturedExecution).hasValue(selected);
    }
}
