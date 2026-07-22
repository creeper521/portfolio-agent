package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelProviderKind;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelProviderRegistrySnapshotTest {

    @Test
    void builtInRegistryContainsExactlyTheTwoReviewedProviders() {
        ModelProviderRegistrySnapshot registry = ModelProviderRegistrySnapshot.builtIn();

        assertThat(registry.getSnapshotVersion()).isEqualTo("c3-model-registry-v1");
        assertThat(registry.getProviderIds()).containsExactlyInAnyOrder(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                ModelProviderKind.GLM_4_7);
        assertThat(registry.supports(
                ModelProviderKind.GLM_4_7,
                "c1-policy-v1",
                "c1.answer.v1")).isTrue();
    }

    @Test
    void duplicateProviderIdFailsInsteadOfFirstWins() {
        ModelProviderDescriptor first = deepSeekDescriptor("adapter-v1");
        ModelProviderDescriptor duplicate = deepSeekDescriptor("adapter-v2");

        assertThatThrownBy(() -> new ModelProviderRegistrySnapshot(
                "test-registry-v1", List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEEPSEEK_V4_FLASH");
    }

    @Test
    void missingProviderIsUnsupportedAndRequiredLookupFails() {
        ModelProviderRegistrySnapshot registry = new ModelProviderRegistrySnapshot(
                "test-registry-v1", List.of(deepSeekDescriptor("adapter-v1")));

        assertThat(registry.supports(ModelProviderKind.GLM_4_7, "c1-policy-v1", "c1.answer.v1"))
                .isFalse();
        assertThatThrownBy(() -> registry.getRequiredDescriptor(ModelProviderKind.GLM_4_7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GLM_4_7");
        assertThatThrownBy(() -> registry.getProviderIds().add(ModelProviderKind.GLM_4_7))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ModelProviderDescriptor deepSeekDescriptor(String adapterVersion) {
        return new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                adapterVersion,
                URI.create("https://api.deepseek.com/chat/completions"),
                "deepseek-v4-flash",
                Set.of("c1-policy-v1"),
                Set.of("c1.answer.v1"),
                Set.of(ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));
    }
}
