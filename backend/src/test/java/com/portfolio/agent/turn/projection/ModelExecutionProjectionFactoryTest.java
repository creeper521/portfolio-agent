package com.portfolio.agent.turn.projection;

import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ModelExecutionProjectionFactoryTest {
    private final ModelExecutionProjectionFactory factory =
            new ModelExecutionProjectionFactory();

    @Test
    void projectsOnlyActuallyAdoptedGoalAndAnswerStages() {
        ResolvedModelExecution execution = modelExecution();

        assertThat(factory.project(execution).getParticipation())
                .isEqualTo(ModelExecutionProjection.Participation.NONE);

        execution.markAttempted(ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        assertThat(factory.project(execution).getParticipation())
                .isEqualTo(ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE);

        execution.markAdopted(ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        assertThat(factory.project(execution).getParticipation())
                .isEqualTo(ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY);

        execution.markAdopted(ResolvedModelExecution.Stage.ANSWER_GENERATION);
        ModelExecutionProjection projection = factory.project(execution);
        assertThat(projection.getParticipation())
                .isEqualTo(ModelExecutionProjection.Participation.GOAL_AND_ANSWER);
        assertThat(projection.getRequestedModelRef()).isEqualTo("glm-4-7-flash");
        assertThat(projection.getSelectionVersion()).isEqualTo("glm-v1");
    }

    @Test
    void noneCarriersAreIsolatedPerTurn() {
        ResolvedModelExecution first = ResolvedModelExecution.none();
        ResolvedModelExecution second = ResolvedModelExecution.none();

        first.markAttempted(ResolvedModelExecution.Stage.GOAL_INTERPRETATION);

        assertThat(first).isNotSameAs(second);
        assertThat(second.wasAttempted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION)).isFalse();
        assertThat(factory.project(first)).isEqualTo(ModelExecutionProjection.none());
    }

    private ResolvedModelExecution modelExecution() {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelRef.of("glm-4-7-flash"), "glm-v1", "GLM", 10,
                URI.create("https://provider.example/v1/chat/completions"),
                "glm-4.7-flash",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                StructuredModelTestFixtures.nativeBindings(),
                100_000, 8_000);
        return ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor),
                new ModelTransportBinding(
                        descriptor.getModelRef(), descriptor.getDescriptorFingerprint(),
                        descriptor.getEndpoint(),
                        descriptor.getModelName(), descriptor.getProtocolProfile(),
                        "test-credential", descriptor.getMaxOutputTokens(),
                        StructuredModelTestFixtures.nativeBindings()));
    }
}
