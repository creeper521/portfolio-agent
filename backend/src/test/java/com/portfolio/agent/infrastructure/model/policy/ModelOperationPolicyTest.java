package com.portfolio.agent.infrastructure.model.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelOperationPolicyTest {

    @Test
    void enabledPolicyKeepsSchemaAndIndependentExecutionBudgets() {
        ModelOperationPolicy policy = new ModelOperationPolicy(
                ModelOperation.GENERAL_KNOWLEDGE,
                OperationMode.ENABLED,
                "general-draft-v1",
                1200,
                Duration.ofSeconds(10));

        assertThat(policy.getOperation()).isEqualTo(ModelOperation.GENERAL_KNOWLEDGE);
        assertThat(policy.getMode()).isEqualTo(OperationMode.ENABLED);
        assertThat(policy.getSchemaVersion()).isEqualTo("general-draft-v1");
        assertThat(policy.getMaxOutputTokens()).isEqualTo(1200);
        assertThat(policy.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.readiness()).isEqualTo(OperationReadiness.AVAILABLE_WITH_PROVIDER);
    }

    @Test
    void enabledPolicyRejectsMissingSchemaOrInvalidBudgetsAtStartup() {
        assertThatThrownBy(() -> policy("", 1200, Duration.ofSeconds(10)).validateStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENERAL_KNOWLEDGE");
        assertThatThrownBy(() -> policy("general-draft-v1", 0, Duration.ofSeconds(10))
                .validateStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENERAL_KNOWLEDGE");
        assertThatThrownBy(() -> policy("general-draft-v1", 1200, Duration.ZERO)
                .validateStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENERAL_KNOWLEDGE");
    }

    @Test
    void disabledPolicyDoesNotRequireExecutionConfiguration() {
        ModelOperationPolicy policy = new ModelOperationPolicy(
                ModelOperation.GENERAL_KNOWLEDGE,
                OperationMode.DISABLED,
                null,
                0,
                null);

        policy.validateStartup();

        assertThat(policy.readiness()).isEqualTo(OperationReadiness.DISABLED);
    }

    private ModelOperationPolicy policy(
            String schemaVersion, int maxOutputTokens, Duration timeout) {
        return new ModelOperationPolicy(
                ModelOperation.GENERAL_KNOWLEDGE,
                OperationMode.ENABLED,
                schemaVersion,
                maxOutputTokens,
                timeout);
    }
}
