package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.configuration.ModelExpressionProperties;
import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCapabilityConfigurationTest {

    private final AgentCapabilityConfiguration configuration =
            new AgentCapabilityConfiguration();
    private final StructuredModelTransport transport = request -> null;

    @Test void disabledProviderIsProjectedAsUnavailableTypedPort() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), new ModelExpressionProperties(),
                new AgentRuntimeProperties(), transport,
                new ConversationProviderAccess(false), enabledGeneralPolicy());

        assertThatThrownBy(() -> port.generate(null))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
    }

    @Test void enabledProviderBuildsTheRealGeneralAdapter() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), new ModelExpressionProperties(),
                new AgentRuntimeProperties(), transport,
                new ConversationProviderAccess(true), enabledGeneralPolicy());

        assertThat(port).isInstanceOf(OpenAiCompatibleGeneralKnowledgeAdapter.class);
    }

    private ModelOperationPolicyRegistry enabledGeneralPolicy() {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE, OperationMode.ENABLED,
                        "provider", "general-v1")));
    }
}
