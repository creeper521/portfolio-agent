package com.portfolio.agent.turn.infrastructure.model;

import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.net.URI;
import java.util.Set;

final class ModelExecutionSnapshotFixture {
    private ModelExecutionSnapshotFixture() { }

    static ResolvedModelExecution model() {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelRef.of("glm-4-7-flash"), "glm-v1", "GLM", 10,
                URI.create("https://provider.example/v1/chat/completions"),
                "glm-4.7-flash",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                Set.of(ModelCapability.TURN_INTERPRETATION,
                        ModelCapability.GENERAL_KNOWLEDGE),
                100_000, 8_000);
        return ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor),
                new ModelTransportBinding(
                        descriptor.getModelRef(), descriptor.getEndpoint(),
                        descriptor.getModelName(), descriptor.getProtocolProfile(),
                        "test-credential", descriptor.getMaxOutputTokens()));
    }
}
