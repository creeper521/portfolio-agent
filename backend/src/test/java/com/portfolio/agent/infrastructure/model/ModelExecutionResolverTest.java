package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelExecutionResolverTest {
    @Test
    void noneDoesNotResolveAProviderBinding() {
        AtomicInteger bindingLookups = new AtomicInteger();
        ModelExecutionResolver resolver = new ModelExecutionResolver(
                ModelCatalogSnapshot.empty(), modelRef -> {
                    bindingLookups.incrementAndGet();
                    throw new AssertionError("NONE must not resolve a provider binding");
                });

        ResolvedModelExecution execution = resolver.resolve(
                AgentTurnCommand.ModelSelection.none());

        assertThat(execution.getSnapshot().getKind())
                .isEqualTo(ModelExecutionSnapshot.Kind.NONE);
        assertThat(execution.getSnapshot().getModelRef()).isEmpty();
        assertThat(bindingLookups).hasValue(0);
    }

    @Test
    void unavailableTakesPriorityOverAStaleSelectionVersion() {
        ModelProviderDescriptor selected = descriptor(
                "glm-4-7-flash", "glm-current-v2",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
        ModelExecutionResolver resolver = new ModelExecutionResolver(
                new ModelCatalogSnapshot(
                        "catalog-v2", List.of(selected),
                        ModelCatalogDefaultSelection.model(selected)),
                modelRef -> {
                    throw new IllegalArgumentException("binding unavailable");
                });

        assertThatThrownBy(() -> resolver.resolve(
                AgentTurnCommand.ModelSelection.model("glm-4-7-flash", "old-v1")))
                .isInstanceOf(ModelExecutionResolutionException.class)
                .extracting(failure -> ((ModelExecutionResolutionException) failure).getCode())
                .isEqualTo(ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
    }

    @Test
    void onlyTheSelectedEntryVersionCanMakeTheSelectionStale() {
        ModelProviderDescriptor selected = descriptor(
                "glm-4-7-flash", "glm-current-v2",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
        ModelProviderDescriptor unrelated = descriptor(
                "qwen-3-7-flash", "qwen-new-v9",
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS);
        ModelCatalogSnapshot catalog = new ModelCatalogSnapshot(
                "catalog-v9", List.of(selected, unrelated),
                ModelCatalogDefaultSelection.model(selected));
        ModelTransportBinding binding = binding(selected);
        AtomicInteger bindingLookups = new AtomicInteger();
        ModelExecutionResolver resolver = new ModelExecutionResolver(
                catalog, modelRef -> {
                    bindingLookups.incrementAndGet();
                    assertThat(modelRef).isEqualTo(selected.getModelRef());
                    return binding;
                });

        ResolvedModelExecution execution = resolver.resolve(
                AgentTurnCommand.ModelSelection.model(
                        "glm-4-7-flash", "glm-current-v2"));

        assertThat(execution.getSnapshot().getModelRef())
                .contains(selected.getModelRef());
        assertThat(execution.getSnapshot().getSelectionVersion())
                .contains("glm-current-v2");
        assertThat(execution.getRequiredBinding()).isSameAs(binding);
        assertThat(bindingLookups).hasValue(1);
    }

    @Test
    void staleSelectionIsCheckedAfterCurrentExecutionAdmission() {
        ModelProviderDescriptor selected = descriptor(
                "glm-4-7-flash", "glm-current-v2",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
        ModelExecutionResolver resolver = new ModelExecutionResolver(
                new ModelCatalogSnapshot(
                        "catalog-v2", List.of(selected),
                        ModelCatalogDefaultSelection.model(selected)),
                modelRef -> binding(selected));

        assertThatThrownBy(() -> resolver.resolve(
                AgentTurnCommand.ModelSelection.model(
                        "glm-4-7-flash", "glm-old-v1")))
                .isInstanceOf(ModelExecutionResolutionException.class)
                .extracting(failure -> ((ModelExecutionResolutionException) failure).getCode())
                .isEqualTo(ModelExecutionResolutionException.Code.MODEL_SELECTION_STALE);
    }

    @Test
    void snapshotAndServerBindingMustShareTheExactDescriptorFingerprint() {
        ModelProviderDescriptor descriptor = descriptor(
                "glm-4-7-flash", "glm-current-v2",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
        ModelTransportBinding mismatched = new ModelTransportBinding(
                descriptor.getModelRef(), "0".repeat(64), descriptor.getEndpoint(),
                descriptor.getModelName(), descriptor.getProtocolProfile(),
                "credential-sentinel", descriptor.getMaxOutputTokens(),
                StructuredModelTestFixtures.nativeBindings());

        assertThatThrownBy(() -> ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor), mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descriptor");
    }

    private ModelProviderDescriptor descriptor(
            String ref, String version, ModelProviderProtocolProfile profile) {
        return new ModelProviderDescriptor(
                ModelRef.of(ref), version, ref, 10,
                URI.create("https://provider.example/v1/chat/completions"),
                ref, profile,
                StructuredModelTestFixtures.nativeBindings(),
                100_000, 8_000);
    }

    private ModelTransportBinding binding(ModelProviderDescriptor descriptor) {
        return new ModelTransportBinding(
                descriptor.getModelRef(), descriptor.getDescriptorFingerprint(),
                descriptor.getEndpoint(),
                descriptor.getModelName(), descriptor.getProtocolProfile(),
                "credential-sentinel", descriptor.getMaxOutputTokens(),
                StructuredModelTestFixtures.nativeBindings());
    }
}
