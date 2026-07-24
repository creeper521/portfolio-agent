package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.gateway.ModelProviderRegistry;

import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModelProviderRegistrySnapshot implements ModelProviderRegistry {

    public static final String BUILT_IN_VERSION = "c3-model-registry-v1";

    private final String snapshotVersion;
    private final Map<ModelProviderKind, ModelProviderDescriptor> descriptors;

    public ModelProviderRegistrySnapshot(
            String snapshotVersion, List<ModelProviderDescriptor> descriptorList) {
        this.snapshotVersion = requireText(snapshotVersion, "snapshotVersion");
        if (descriptorList == null || descriptorList.isEmpty()) {
            throw new IllegalArgumentException("descriptorList must not be empty");
        }
        EnumMap<ModelProviderKind, ModelProviderDescriptor> byProvider =
                new EnumMap<>(ModelProviderKind.class);
        for (ModelProviderDescriptor descriptor : descriptorList) {
            ModelProviderDescriptor nonNullDescriptor = Objects.requireNonNull(descriptor, "descriptor");
            ModelProviderKind providerId = nonNullDescriptor.getProviderId();
            if (byProvider.containsKey(providerId)) {
                throw new IllegalArgumentException("duplicate provider: " + providerId);
            }
            byProvider.put(providerId, nonNullDescriptor);
        }
        this.descriptors = Map.copyOf(byProvider);
    }

    public static ModelProviderRegistrySnapshot builtIn() {
        Set<ModelProviderCapability> capabilities = Set.of(
                ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                ModelProviderCapability.THINKING_CONTROL,
                ModelProviderCapability.NON_STREAMING);
        return new ModelProviderRegistrySnapshot(BUILT_IN_VERSION, List.of(
                descriptor(ModelProviderKind.DEEPSEEK_V4_FLASH,
                        "https://api.deepseek.com/chat/completions",
                        "deepseek-v4-flash", capabilities),
                descriptor(ModelProviderKind.GLM_4_7,
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                        "glm-4.7", capabilities)));
    }

    @Override
    public String getSnapshotVersion() {
        return snapshotVersion;
    }

    @Override
    public boolean supports(
            ModelProviderKind provider,
            String modelPolicyVersion,
            String answerSchemaVersion) {
        ModelProviderDescriptor descriptor = descriptors.get(provider);
        return descriptor != null && descriptor.supports(modelPolicyVersion, answerSchemaVersion);
    }

    public Set<ModelProviderKind> getProviderIds() {
        return descriptors.keySet();
    }

    ModelProviderDescriptor getRequiredDescriptor(ModelProviderKind provider) {
        ModelProviderDescriptor descriptor = descriptors.get(provider);
        if (descriptor == null) {
            throw new IllegalArgumentException("provider is not configured: " + provider);
        }
        return descriptor;
    }

    private static ModelProviderDescriptor descriptor(
            ModelProviderKind providerId,
            String endpoint,
            String modelName,
            Set<ModelProviderCapability> capabilities) {
        return new ModelProviderDescriptor(
                providerId,
                "c3-openai-compatible-v1",
                URI.create(endpoint),
                modelName,
                Set.of("c1-policy-v1"),
                Set.of("c1.answer.v1", ConversationalAgentProperties.ANSWER_SCHEMA_VERSION),
                capabilities);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
