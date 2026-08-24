package com.portfolio.agent.infrastructure.model.provider;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

public final class ModelProviderDescriptor {

    private static final Set<ModelProviderRequestFeature> REQUIRED_REQUEST_FEATURES = Set.of(
            ModelProviderRequestFeature.JSON_OBJECT_REQUEST,
            ModelProviderRequestFeature.THINKING_DISABLED_REQUEST,
            ModelProviderRequestFeature.NON_STREAMING_REQUEST);

    private final ModelProviderKind providerId;
    private final String adapterVersion;
    private final URI endpoint;
    private final String modelName;
    private final Set<String> approvedModelPolicyVersions;
    private final Set<String> approvedAnswerSchemaVersions;
    private final Set<ModelProviderRequestFeature> requestFeatures;

    public ModelProviderDescriptor(
            ModelProviderKind providerId,
            String adapterVersion,
            URI endpoint,
            String modelName,
            Set<String> policyVersions,
            Set<String> schemaVersions,
            Set<ModelProviderRequestFeature> requestFeatures) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.adapterVersion = requireText(adapterVersion, "adapterVersion");
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.approvedModelPolicyVersions = copyTextSet(policyVersions, "policyVersions");
        this.approvedAnswerSchemaVersions = copyTextSet(schemaVersions, "schemaVersions");
        this.requestFeatures = copyRequestFeatures(requestFeatures);
    }

    public boolean isApprovedConfiguration(String policyVersion, String schemaVersion) {
        return approvedModelPolicyVersions.contains(policyVersion)
                && approvedAnswerSchemaVersions.contains(schemaVersion)
                && requestFeatures.containsAll(REQUIRED_REQUEST_FEATURES);
    }

    public ModelProviderKind getProviderId() { return providerId; }
    public String getAdapterVersion() { return adapterVersion; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public Set<String> getApprovedModelPolicyVersions() { return approvedModelPolicyVersions; }
    public Set<String> getApprovedAnswerSchemaVersions() { return approvedAnswerSchemaVersions; }
    public Set<ModelProviderRequestFeature> getRequestFeatures() { return requestFeatures; }

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

    private static Set<ModelProviderRequestFeature> copyRequestFeatures(
            Set<ModelProviderRequestFeature> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("requestFeatures must not be empty");
        }
        for (ModelProviderRequestFeature value : values) {
            if (value == null) {
                throw new IllegalArgumentException("requestFeatures must not contain null");
            }
        }
        Set<ModelProviderRequestFeature> copied = Set.copyOf(values);
        if (!copied.containsAll(REQUIRED_REQUEST_FEATURES)) {
            throw new IllegalArgumentException(
                    "requestFeatures must contain all required request features");
        }
        return copied;
    }
}
