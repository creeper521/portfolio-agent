package com.portfolio.agent.infrastructure.model.provider;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelProviderDescriptorTest {

    @Test
    void bindingFingerprintsFlowIntoDescriptorAndTransportIdentity() {
        Map<ModelOperation, OperationBinding> promotedBindings =
                StructuredModelTestFixtures.qwenV8ToolBindings();
        ModelProviderDescriptor promoted = descriptor(
                "qwen-3-7-flash", promotedBindings);
        ModelProviderDescriptor priorBindingShape = descriptor(
                "qwen-3-7-flash",
                StructuredModelTestFixtures.v4NativeBindings());
        ModelTransportBinding transport = new ModelTransportBinding(
                promoted.getModelRef(), promoted.getDescriptorFingerprint(),
                promoted.getEndpoint(), promoted.getModelName(),
                promoted.getProtocolProfile(), "test-secret",
                promoted.getMaxOutputTokens(), promoted.getOperationBindings());

        assertThat(promoted.getDescriptorFingerprint())
                .isNotEqualTo(priorBindingShape.getDescriptorFingerprint());
        assertThat(transport.getDescriptorFingerprint())
                .isEqualTo(promoted.getDescriptorFingerprint());
        assertThat(transport.getOperationBindings().keySet())
                .containsExactlyInAnyOrderElementsOf(promotedBindings.keySet());
        promotedBindings.forEach((operation, expected) -> assertThat(
                transport.getRequiredOperationBinding(operation)
                        .getBindingFingerprint())
                .isEqualTo(expected.getBindingFingerprint()));
        assertThat(promoted.getOperationBindings()
                .get(ModelOperation.TURN_INTERPRETATION)
                .getProviderContractRef().schemaVersion())
                .isEqualTo("goal.provider-draft.v3");
        assertThat(promoted.getOperationBindings()
                .get(ModelOperation.GENERAL_KNOWLEDGE)
                .getProviderContractRef().schemaVersion())
                .isEqualTo("general.provider-draft.v4");
        assertThat(promoted.getOperationBindings()
                .get(ModelOperation.GENERAL_KNOWLEDGE)
                .getApplicationContractRef().schemaVersion())
                .isEqualTo("general.draft.v3");
        assertThat(promoted.getOperationBindings().values())
                .extracting(binding -> binding.getProviderContractRef()
                        .schemaVersion())
                .doesNotContain("goal.provider-draft.v1");
    }

    @Test
    void usesAValidatedModelRefAndContainsOnlyNonSecretExecutionMetadata() {
        ModelProviderDescriptor descriptor = descriptor("glm-4-7-flash");

        assertThat(descriptor.getModelRef()).isEqualTo(ModelRef.of("glm-4-7-flash"));
        assertThat(descriptor.getProtocolProfile())
                .isEqualTo(ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
        assertThat(descriptor.getDescriptorFingerprint()).matches("[0-9a-f]{64}");
        assertThat(ModelProviderDescriptor.class.getDeclaredFields())
                .extracting(Field::getName)
                .noneMatch(this::looksLikeCredentialField);
        assertThat(ModelTransportBinding.class.getDeclaredFields())
                .extracting(Field::getName)
                .contains("apiKey");
        assertThat(ModelProviderDescriptor.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("toString", "supports", "isApprovedConfiguration");
    }

    @Test
    void rejectsInvalidRefEndpointAndExecutionBounds() {
        assertThatThrownBy(() -> ModelRef.of("GLM_4_7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model ref");
        assertThatThrownBy(() -> new ModelProviderDescriptor(
                ModelRef.of("glm"), "v1", "GLM", 10,
                URI.create("http://example.test/chat"), "glm",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                StructuredModelTestFixtures.nativeBindings(), 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new ModelProviderDescriptor(
                ModelRef.of("glm"), "v1", "GLM", 10,
                URI.create("https://example.test/chat"), "glm",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                StructuredModelTestFixtures.nativeBindings(), 100, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputTokens");
    }

    @Test
    void queryCredentialsCannotEnterDescriptorFingerprintOrBindingText() {
        String queryCredential = "query-secret";
        URI endpointWithQuery = URI.create(
                "https://example.test/chat?api_key=" + queryCredential);

        assertThatThrownBy(() -> new ModelProviderDescriptor(
                ModelRef.of("glm"), "v1", "GLM", 10,
                endpointWithQuery, "glm",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                StructuredModelTestFixtures.nativeBindings(), 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(queryCredential)
                .hasMessageNotContaining("api_key");
        assertThatThrownBy(() -> new ModelTransportBinding(
                ModelRef.of("glm"), "0".repeat(64), endpointWithQuery, "glm",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                "header-secret", 10,
                StructuredModelTestFixtures.nativeBindings()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(queryCredential)
                .hasMessageNotContaining("api_key");

        ModelProviderDescriptor descriptor = descriptor("glm");
        assertThat(descriptor.getDescriptorFingerprint()).doesNotContain(queryCredential);
        assertThat(descriptor.toString()).doesNotContain(queryCredential);
    }

    @Test
    void publicEntryIsImmutableAndExcludesInternalTransportMetadata() {
        ModelCatalogEntry entry = descriptor("glm").publicEntry();

        assertThat(entry.modelRef()).isEqualTo("glm");
        assertThat(entry.selectionVersion()).isEqualTo("glm-v1");
        assertThat(entry.capabilities()).containsExactlyInAnyOrder(
                ModelCapability.TURN_INTERPRETATION,
                ModelCapability.GENERAL_KNOWLEDGE);
        assertThat(entry.toString())
                .doesNotContain("example.test", "glm-4.7-flash", "fingerprint");
        assertThatThrownBy(() -> entry.capabilities().add(
                ModelCapability.GENERAL_KNOWLEDGE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ModelProviderDescriptor descriptor(String ref) {
        return descriptor(ref, StructuredModelTestFixtures.nativeBindings());
    }

    private ModelProviderDescriptor descriptor(
            String ref, Map<ModelOperation, OperationBinding> bindings) {
        return new ModelProviderDescriptor(
                ModelRef.of(ref), "glm-v1", "GLM", 10,
                URI.create("https://example.test/chat"), "glm-4.7-flash",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                bindings,
                200_000, 8_000);
    }

    private boolean looksLikeCredentialField(String name) {
        String normalized = name.toLowerCase();
        return normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("key")
                || normalized.contains("credential")
                || normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("policy")
                || normalized.contains("schema");
    }
}
