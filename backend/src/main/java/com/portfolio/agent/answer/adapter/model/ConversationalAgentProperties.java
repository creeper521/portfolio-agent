package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.conversational-agent")
public final class ConversationalAgentProperties {

    public static final String ANSWER_SCHEMA_VERSION = "conversation.answer.v2";

    private boolean enabled;
    private boolean visitorDataPolicyApproved;
    private int maxHistoryRounds = 20;
    private int recentRawRounds = 6;
    private int maxInputTokens = 12000;
    private int maxToolCalls = 4;
    private int maxToolRounds = 2;
    private int maxSuggestedQuestions = 3;
    private double minimumIntentConfidence = 0.65;

    public boolean allowsProviderCalls(
            ModelPolicy modelPolicy,
            ModelProviderRegistrySnapshot registry
    ) {
        return enabled
                && visitorDataPolicyApproved
                && modelPolicy.isModelEnabled()
                && registry.supports(
                        modelPolicy.getProvider(),
                        modelPolicy.getModelPolicyVersion(),
                        ANSWER_SCHEMA_VERSION);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isVisitorDataPolicyApproved() { return visitorDataPolicyApproved; }
    public void setVisitorDataPolicyApproved(boolean visitorDataPolicyApproved) {
        this.visitorDataPolicyApproved = visitorDataPolicyApproved;
    }
    public int getMaxHistoryRounds() { return maxHistoryRounds; }
    public void setMaxHistoryRounds(int maxHistoryRounds) {
        this.maxHistoryRounds = maxHistoryRounds;
    }
    public int getRecentRawRounds() { return recentRawRounds; }
    public void setRecentRawRounds(int recentRawRounds) {
        this.recentRawRounds = recentRawRounds;
    }
    public int getMaxInputTokens() { return maxInputTokens; }
    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }
    public int getMaxToolRounds() { return maxToolRounds; }
    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }
    public int getMaxSuggestedQuestions() { return maxSuggestedQuestions; }
    public void setMaxSuggestedQuestions(int maxSuggestedQuestions) {
        this.maxSuggestedQuestions = maxSuggestedQuestions;
    }
    public double getMinimumIntentConfidence() { return minimumIntentConfidence; }
    public void setMinimumIntentConfidence(double minimumIntentConfidence) {
        this.minimumIntentConfidence = minimumIntentConfidence;
    }
}
