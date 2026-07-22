package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelProviderKind;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

public final class ModelProviderDescriptor {

    private static final Set<ModelProviderCapability> REQUIRED_CAPABILITIES = Set.of(
            ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
            ModelProviderCapability.THINKING_CONTROL,
            ModelProviderCapability.NON_STREAMING);

    private final ModelProviderKind providerId;
    private final String adapterVersion;
    private final URI endpoint;
    private final String modelName;
    private final Set<String> supportedModelPolicyVersions;
    private final Set<String> supportedAnswerSchemaVersions;
    private final Set<ModelProviderCapability> capabilities;

    public ModelProviderDescriptor(
            ModelProviderKind providerId,
            String adapterVersion,
            URI endpoint,
            String modelName,
            Set<String> policyVersions,
            Set<String> schemaVersions,
            Set<ModelProviderCapability> capabilities) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.adapterVersion = requireText(adapterVersion, "adapterVersion");
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.supportedModelPolicyVersions = copyTextSet(policyVersions, "policyVersions");
        this.supportedAnswerSchemaVersions = copyTextSet(schemaVersions, "schemaVersions");
        this.capabilities = copyCapabilities(capabilities);
    }

    public boolean supports(String policyVersion, String schemaVersion) {
        return supportedModelPolicyVersions.contains(policyVersion)
                && supportedAnswerSchemaVersions.contains(schemaVersion)
                && capabilities.containsAll(REQUIRED_CAPABILITIES);
    }

    public ModelProviderKind getProviderId() { return providerId; }
    public String getAdapterVersion() { return adapterVersion; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public Set<String> getSupportedModelPolicyVersions() { return supportedModelPolicyVersions; }
    public Set<String> getSupportedAnswerSchemaVersions() { return supportedAnswerSchemaVersions; }
    public Set<ModelProviderCapability> getCapabilities() { return capabilities; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static URI requireHttpsEndpoint(URI value) {
        if (value == null
                || value.getScheme() == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()) {
            throw new IllegalArgumentException("endpoint must be an HTTPS URI with a host");
        }
        return value;
    }

    private static Set<String> copyTextSet(Set<String> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        for (String value : values) {
            requireText(value, name + " element");
        }
        return Set.copyOf(values);
    }

    private static Set<ModelProviderCapability> copyCapabilities(
            Set<ModelProviderCapability> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        for (ModelProviderCapability value : values) {
            if (value == null) {
                throw new IllegalArgumentException("capabilities must not contain null");
            }
        }
        Set<ModelProviderCapability> copied = Set.copyOf(values);
        if (!copied.containsAll(REQUIRED_CAPABILITIES)) {
            throw new IllegalArgumentException("capabilities must contain all required capabilities");
        }
        return copied;
    }
}
