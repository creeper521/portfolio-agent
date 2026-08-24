package com.portfolio.agent.infrastructure.model.provider;

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
                Set.of(ModelProviderRequestFeature.JSON_OBJECT_REQUEST,
                        ModelProviderRequestFeature.THINKING_DISABLED_REQUEST,
                        ModelProviderRequestFeature.NON_STREAMING_REQUEST));

        assertThat(descriptor.isApprovedConfiguration("c1-policy-v1", "c1.answer.v1"))
                .isTrue();
        assertThat(descriptor.isApprovedConfiguration("unknown", "c1.answer.v1"))
                .isFalse();
        assertThat(ModelProviderDescriptor.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("apiKey", "secret", "token", "prompt", "request", "response");
        assertThat(ModelProviderDescriptor.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("toString", "supports", "isSchemaVerified", "isQualityVerified");
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
                Set.of(ModelProviderRequestFeature.JSON_OBJECT_REQUEST,
                        ModelProviderRequestFeature.THINKING_DISABLED_REQUEST,
                        ModelProviderRequestFeature.NON_STREAMING_REQUEST));

        assertThat(descriptor.getProviderId()).isEqualTo(ModelProviderKind.DEEPSEEK_V4_FLASH);
        assertThat(descriptor.getAdapterVersion()).isEqualTo("adapter-v1");
        assertThat(descriptor.getEndpoint()).isEqualTo(URI.create("https://api.deepseek.com/chat/completions"));
        assertThat(descriptor.getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(descriptor.getApprovedModelPolicyVersions()).containsExactly("c1-policy-v1");
        assertThat(descriptor.getApprovedAnswerSchemaVersions()).containsExactly("c1.answer.v1");
        assertThat(descriptor.getRequestFeatures()).containsExactlyInAnyOrder(
                ModelProviderRequestFeature.JSON_OBJECT_REQUEST,
                ModelProviderRequestFeature.THINKING_DISABLED_REQUEST,
                ModelProviderRequestFeature.NON_STREAMING_REQUEST);
        assertThatThrownBy(() -> descriptor.getRequestFeatures().add(
                ModelProviderRequestFeature.NON_STREAMING_REQUEST))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
