package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelPolicy;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderRegistrySnapshot;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.conversational-agent")
public final class ConversationalAgentProperties {

    private boolean enabled;
    private boolean visitorDataPolicyApproved;

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
                        ModelProviderRegistrySnapshot.CONVERSATION_ANSWER_SCHEMA_VERSION);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isVisitorDataPolicyApproved() { return visitorDataPolicyApproved; }
    public void setVisitorDataPolicyApproved(boolean visitorDataPolicyApproved) {
        this.visitorDataPolicyApproved = visitorDataPolicyApproved;
    }
}
