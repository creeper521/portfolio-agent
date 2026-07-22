package com.portfolio.agent.answer.domain;

import java.time.Duration;
import java.util.Objects;

public final class ModelPolicy {

    private final String modelPolicyVersion;
    private final String answerSchemaVersion;
    private final ModelProviderKind provider;
    private final boolean configuredEnabled;
    private final boolean externalDataPolicyApproved;
    private final boolean selectedApiKeyPresent;
    private final Duration timeout;
    private final int maxTokens;
    private final int maxModelAttempts;

    public ModelPolicy(
            String modelPolicyVersion,
            String answerSchemaVersion,
            ModelProviderKind provider,
            boolean configuredEnabled,
            boolean externalDataPolicyApproved,
            boolean selectedApiKeyPresent,
            Duration timeout,
            int maxTokens,
            int maxModelAttempts
    ) {
        this.modelPolicyVersion = requireText(modelPolicyVersion, "modelPolicyVersion");
        this.answerSchemaVersion = requireText(answerSchemaVersion, "answerSchemaVersion");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.configuredEnabled = configuredEnabled;
        this.externalDataPolicyApproved = externalDataPolicyApproved;
        this.selectedApiKeyPresent = selectedApiKeyPresent;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.maxTokens = maxTokens;
        this.maxModelAttempts = maxModelAttempts;
    }

    public boolean isModelEnabled() {
        return configuredEnabled
                && externalDataPolicyApproved
                && selectedApiKeyPresent
                && !timeout.isNegative()
                && !timeout.isZero()
                && maxTokens > 0
                && maxModelAttempts == 1;
    }

    public String getModelPolicyVersion() { return modelPolicyVersion; }
    public String getAnswerSchemaVersion() { return answerSchemaVersion; }
    public ModelProviderKind getProvider() { return provider; }
    public boolean isConfiguredEnabled() { return configuredEnabled; }
    public boolean isExternalDataPolicyApproved() { return externalDataPolicyApproved; }
    public boolean isSelectedApiKeyPresent() { return selectedApiKeyPresent; }
    public Duration getTimeout() { return timeout; }
    public int getMaxTokens() { return maxTokens; }
    public int getMaxModelAttempts() { return maxModelAttempts; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
