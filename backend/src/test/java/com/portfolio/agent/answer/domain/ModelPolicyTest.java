package com.portfolio.agent.answer.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPolicyTest {

    @Test
    void enablesOnlyWhenTheOperatorExplicitlyApprovesEveryGate() {
        ModelPolicy enabled = policy(true, true, true, 1);
        ModelPolicy disabledByOperator = policy(false, true, true, 1);
        ModelPolicy missingDataApproval = policy(true, false, true, 1);
        ModelPolicy missingSelectedKey = policy(true, true, false, 1);
        ModelPolicy invalidAttemptBudget = policy(true, true, true, 2);

        assertThat(enabled.isModelEnabled()).isTrue();
        assertThat(disabledByOperator.isModelEnabled()).isFalse();
        assertThat(missingDataApproval.isModelEnabled()).isFalse();
        assertThat(missingSelectedKey.isModelEnabled()).isFalse();
        assertThat(invalidAttemptBudget.isModelEnabled()).isFalse();
        assertThat(enabled.getMaxModelAttempts()).isEqualTo(1);
        assertThat(enabled.getProvider()).isEqualTo(ModelProviderKind.DEEPSEEK_V4_FLASH);
    }

    private ModelPolicy policy(
            boolean configuredEnabled,
            boolean externalDataPolicyApproved,
            boolean selectedApiKeyPresent,
            int maxModelAttempts
    ) {
        return new ModelPolicy(
                "c1-policy-v1",
                "c1.answer.v1",
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                configuredEnabled,
                externalDataPolicyApproved,
                selectedApiKeyPresent,
                Duration.ofSeconds(4),
                1200,
                maxModelAttempts
        );
    }
}
