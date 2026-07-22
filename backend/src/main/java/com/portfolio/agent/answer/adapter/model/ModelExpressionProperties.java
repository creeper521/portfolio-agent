package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelProviderKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "portfolio.model-expression")
public final class ModelExpressionProperties {

    private boolean enabled;
    private ModelProviderKind provider = ModelProviderKind.DEEPSEEK_V4_FLASH;
    private String modelPolicyVersion = "c1-policy-v1";
    private String answerSchemaVersion = "c1.answer.v1";
    private Duration timeout = Duration.ofSeconds(8);
    private int maxTokens = 1200;
    private int maxModelAttempts = 1;
    private boolean externalDataPolicyApproved;
    private String deepseekApiKey = "";
    private String glmApiKey = "";

    public String selectedApiKey() {
        return apiKeyFor(provider);
    }

    public String apiKeyFor(ModelProviderKind provider) {
        Objects.requireNonNull(provider, "provider");
        Map<ModelProviderKind, String> configured = new EnumMap<>(ModelProviderKind.class);
        configured.put(ModelProviderKind.DEEPSEEK_V4_FLASH, normalize(deepseekApiKey));
        configured.put(ModelProviderKind.GLM_4_7, normalize(glmApiKey));
        return configured.get(provider);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ModelProviderKind getProvider() { return provider; }
    public void setProvider(ModelProviderKind provider) { this.provider = provider; }
    public String getModelPolicyVersion() { return modelPolicyVersion; }
    public void setModelPolicyVersion(String modelPolicyVersion) {
        this.modelPolicyVersion = modelPolicyVersion;
    }
    public String getAnswerSchemaVersion() { return answerSchemaVersion; }
    public void setAnswerSchemaVersion(String answerSchemaVersion) {
        this.answerSchemaVersion = answerSchemaVersion;
    }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getMaxModelAttempts() { return maxModelAttempts; }
    public void setMaxModelAttempts(int maxModelAttempts) {
        this.maxModelAttempts = maxModelAttempts;
    }
    public boolean isExternalDataPolicyApproved() { return externalDataPolicyApproved; }
    public void setExternalDataPolicyApproved(boolean externalDataPolicyApproved) {
        this.externalDataPolicyApproved = externalDataPolicyApproved;
    }
    public String getDeepseekApiKey() { return deepseekApiKey; }
    public void setDeepseekApiKey(String deepseekApiKey) {
        this.deepseekApiKey = deepseekApiKey;
    }
    public String getGlmApiKey() { return glmApiKey; }
    public void setGlmApiKey(String glmApiKey) { this.glmApiKey = glmApiKey; }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
