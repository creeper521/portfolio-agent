package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalAgentConfigurationTest {

    @Test
    void providerCallsRequireBothApprovalsKeyAndCompatibleRegistry() {
        ConversationalAgentProperties properties = approvedProperties();
        ModelProviderRegistrySnapshot registry = ModelProviderRegistrySnapshot.builtIn();

        assertThat(properties.allowsProviderCalls(policy(true, true), registry)).isTrue();

        properties.setEnabled(false);
        assertThat(properties.allowsProviderCalls(policy(true, true), registry)).isFalse();
        properties.setEnabled(true);

        properties.setVisitorDataPolicyApproved(false);
        assertThat(properties.allowsProviderCalls(policy(true, true), registry)).isFalse();
        properties.setVisitorDataPolicyApproved(true);

        assertThat(properties.allowsProviderCalls(policy(false, true), registry)).isFalse();
        assertThat(properties.allowsProviderCalls(policy(true, false), registry)).isFalse();
    }

    private ConversationalAgentProperties approvedProperties() {
        ConversationalAgentProperties properties = new ConversationalAgentProperties();
        properties.setEnabled(true);
        properties.setVisitorDataPolicyApproved(true);
        return properties;
    }

    private ModelPolicy policy(boolean approved, boolean keyAndRegistryCompatible) {
        return new ModelPolicy(
                "c1-policy-v1",
                "c1.answer.v1",
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                true,
                approved,
                keyAndRegistryCompatible,
                Duration.ofSeconds(8),
                1200,
                1);
    }
}
