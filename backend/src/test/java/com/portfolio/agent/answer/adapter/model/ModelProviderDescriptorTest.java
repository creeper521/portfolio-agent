package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelProviderKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelProviderDescriptorTest {

    @Test
    void validatesNonSecretMetadataAndExactCompatibility() {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                "c3-openai-compatible-v1",
                URI.create("https://api.deepseek.com/chat/completions"),
                "deepseek-v4-flash",
                Set.of("c1-policy-v1"),
                Set.of("c1.answer.v1"),
                Set.of(ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));

        assertThat(descriptor.supports("c1-policy-v1", "c1.answer.v1")).isTrue();
        assertThat(descriptor.supports("unknown", "c1.answer.v1")).isFalse();
        assertThat(ModelProviderDescriptor.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("apiKey", "secret", "token", "prompt", "request", "response");
        assertThat(ModelProviderDescriptor.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName).doesNotContain("toString");
    }

    @Test
    void rejectsNonHttpsEndpointAndIncompleteCapabilities() {
        assertThatThrownBy(() -> new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                "adapter-v1",
                URI.create("http://api.deepseek.com/chat/completions"),
                "deepseek-v4-flash",
                Set.of("c1-policy-v1"),
                Set.of("c1.answer.v1"),
                Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesValidatedMetadataForImmutableAccess() {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                "adapter-v1",
                URI.create("https://api.deepseek.com/chat/completions"),
                "deepseek-v4-flash",
                Set.of("c1-policy-v1"),
                Set.of("c1.answer.v1"),
                Set.of(ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));

        assertThat(descriptor.getProviderId()).isEqualTo(ModelProviderKind.DEEPSEEK_V4_FLASH);
        assertThat(descriptor.getAdapterVersion()).isEqualTo("adapter-v1");
        assertThat(descriptor.getEndpoint()).isEqualTo(URI.create("https://api.deepseek.com/chat/completions"));
        assertThat(descriptor.getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(descriptor.getSupportedModelPolicyVersions()).containsExactly("c1-policy-v1");
        assertThat(descriptor.getSupportedAnswerSchemaVersions()).containsExactly("c1.answer.v1");
        assertThat(descriptor.getCapabilities()).containsExactlyInAnyOrder(
                ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                ModelProviderCapability.THINKING_CONTROL,
                ModelProviderCapability.NON_STREAMING);
        assertThatThrownBy(() -> descriptor.getCapabilities().add(ModelProviderCapability.NON_STREAMING))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
