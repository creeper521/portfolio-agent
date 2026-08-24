package com.portfolio.agent.turn.infrastructure;

import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderKind;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimeReadinessTest {

    @Test
    void providerMismatchFailsApplicationContextStartup() {
        ModelOperationPolicyRegistry policies = policies(
                "GLM_4_7", GoalProposalCodec.SCHEMA_VERSION,
                "DEEPSEEK_V4_FLASH", GeneralDraftCodec.SCHEMA_VERSION);

        new ApplicationContextRunner()
                .withBean(AgentRuntimeReadiness.class,
                        () -> readiness(policies, ModelProviderKind.DEEPSEEK_V4_FLASH))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "enabled model operation provider does not match transport: "
                                            + "TURN_INTERPRETATION");
                });
    }

    @Test
    void enabledOperationProviderMustMatchTheOnlyTransportProvider() {
        ModelOperationPolicyRegistry policies = policies(
                "GLM_4_7", GoalProposalCodec.SCHEMA_VERSION,
                "DEEPSEEK_V4_FLASH", GeneralDraftCodec.SCHEMA_VERSION);

        assertThatThrownBy(() -> readiness(policies, ModelProviderKind.DEEPSEEK_V4_FLASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURN_INTERPRETATION")
                .hasMessageContaining("provider");
    }

    @Test
    void enabledOperationSchemaMustMatchItsProductionCodec() {
        ModelOperationPolicyRegistry policies = policies(
                "DEEPSEEK_V4_FLASH", "goal.proposal.wrong",
                "DEEPSEEK_V4_FLASH", GeneralDraftCodec.SCHEMA_VERSION);

        assertThatThrownBy(() -> readiness(policies, ModelProviderKind.DEEPSEEK_V4_FLASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURN_INTERPRETATION")
                .hasMessageContaining("schema");
    }

    @Test
    void matchedAuthorityProducesOneFrozenReadinessSnapshot() {
        AgentRuntimeReadiness readiness = readiness(policies(
                "DEEPSEEK_V4_FLASH", GoalProposalCodec.SCHEMA_VERSION,
                "DEEPSEEK_V4_FLASH", GeneralDraftCodec.SCHEMA_VERSION),
                ModelProviderKind.DEEPSEEK_V4_FLASH);

        assertThat(readiness.isAgentAvailable()).isTrue();
        assertThat(readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION))
                .isTrue();
        assertThat(readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE))
                .isTrue();
    }

    private AgentRuntimeReadiness readiness(
            ModelOperationPolicyRegistry policies, ModelProviderKind provider) {
        return new AgentRuntimeReadiness(
                ConversationContextProperties.Mode.POSTGRESQL,
                new ConversationProviderAccess(true), policies, provider);
    }

    private ModelOperationPolicyRegistry policies(
            String turnProvider, String turnSchema,
            String generalProvider, String generalSchema) {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.TURN_INTERPRETATION,
                new ModelOperationPolicy(
                        ModelOperation.TURN_INTERPRETATION, OperationMode.ENABLED,
                        turnProvider, turnSchema),
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE, OperationMode.ENABLED,
                        generalProvider, generalSchema)));
    }
}
