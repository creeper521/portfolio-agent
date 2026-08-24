package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.net.URI;
import java.util.Objects;

/** Server-only binding. Credential access is deliberately package-private. */
public final class ModelTransportBinding {
    private final ModelRef modelRef;
    private final URI endpoint;
    private final String modelName;
    private final ModelProviderProtocolProfile protocolProfile;
    private final String apiKey;
    private final int maxOutputTokens;

    public ModelTransportBinding(
            ModelRef modelRef,
            URI endpoint,
            String modelName,
            ModelProviderProtocolProfile protocolProfile,
            String apiKey,
            int maxOutputTokens) {
        this.modelRef = Objects.requireNonNull(modelRef, "modelRef");
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.protocolProfile = Objects.requireNonNull(protocolProfile, "protocolProfile");
        this.apiKey = requireText(apiKey, "credential");
        if (maxOutputTokens < 1 || maxOutputTokens > 128_000) {
            throw new IllegalArgumentException("maxOutputTokens is invalid");
        }
        this.maxOutputTokens = maxOutputTokens;
    }

    public ModelRef getModelRef() { return modelRef; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public ModelProviderProtocolProfile getProtocolProfile() { return protocolProfile; }
    public int getMaxOutputTokens() { return maxOutputTokens; }

    String authorizationHeaderValue() {
        return "Bearer " + apiKey;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private static URI requireHttpsEndpoint(URI value) {
        if (value == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an HTTPS URI with a host");
        }
        return value;
    }
}
