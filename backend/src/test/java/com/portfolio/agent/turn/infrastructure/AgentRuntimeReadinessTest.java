package com.portfolio.agent.turn.infrastructure;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimeReadinessTest {

    @Test
    void enabledOperationSchemaMismatchFailsApplicationContextStartup() {
        ModelOperationPolicyRegistry policies = policies(
                "goal.proposal.wrong", GeneralDraftCodec.SCHEMA_VERSION);

        new ApplicationContextRunner()
                .withBean(AgentRuntimeReadiness.class, () -> readiness(policies))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "enabled model operation schema does not match "
                                            + "production codec: TURN_INTERPRETATION");
                });
    }

    @Test
    void enabledOperationSchemaMustMatchItsProductionCodec() {
        ModelOperationPolicyRegistry policies = policies(
                "goal.proposal.wrong", GeneralDraftCodec.SCHEMA_VERSION);

        assertThatThrownBy(() -> readiness(policies))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURN_INTERPRETATION")
                .hasMessageContaining("schema");
    }

    @Test
    void contextAndOperationPoliciesProduceOneFrozenReadinessSnapshot() {
        AgentRuntimeReadiness readiness = readiness(policies(
                GoalProposalCodec.SCHEMA_VERSION,
                GeneralDraftCodec.SCHEMA_VERSION));

        assertThat(readiness.isAgentAvailable()).isTrue();
        assertThat(readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION))
                .isTrue();
        assertThat(readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE))
                .isTrue();
    }

    @Test
    void disabledContextKeepsEveryOperationUnavailable() {
        AgentRuntimeReadiness readiness = new AgentRuntimeReadiness(
                ConversationContextProperties.Mode.DISABLED,
                policies(GoalProposalCodec.SCHEMA_VERSION,
                        GeneralDraftCodec.SCHEMA_VERSION));

        assertThat(readiness.isAgentAvailable()).isFalse();
        assertThat(readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION))
                .isFalse();
        assertThat(readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE))
                .isFalse();
    }

    private AgentRuntimeReadiness readiness(ModelOperationPolicyRegistry policies) {
        return new AgentRuntimeReadiness(
                ConversationContextProperties.Mode.POSTGRESQL,
                policies);
    }

    private ModelOperationPolicyRegistry policies(
            String turnSchema, String generalSchema) {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.TURN_INTERPRETATION,
                new ModelOperationPolicy(
                        ModelOperation.TURN_INTERPRETATION,
                        OperationMode.ENABLED,
                        turnSchema,
                        1600,
                        Duration.ofSeconds(8)),
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        OperationMode.ENABLED,
                        generalSchema,
                        1200,
                        Duration.ofSeconds(10))));
    }
}
