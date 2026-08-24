package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.SystemPromptCatalog;
import com.portfolio.agent.infrastructure.model.configuration.ModelExpressionProperties;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCapabilityConfigurationTest {

    private final AgentCapabilityConfiguration configuration =
            new AgentCapabilityConfiguration();
    private final StructuredModelTransport transport = request -> null;
    private final SystemPromptCatalog prompts = new SystemPromptCatalog();

    @Test void promptCatalogLoadsEvenWhenModelOperationsAreDisabled() {
        SystemPromptCatalog catalog = configuration.systemPromptCatalog();

        assertThat(catalog.goalInterpretation()).isNotBlank();
        assertThat(catalog.generalKnowledge()).isNotBlank();
    }

    @Test void disabledProviderIsProjectedAsUnavailableTypedPort() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), new ModelExpressionProperties(),
                new AgentRuntimeProperties(), transport, prompts,
                readiness(false));

        assertThatThrownBy(() -> port.generate(null))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
    }

    @Test void enabledProviderBuildsTheRealGeneralAdapter() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), new ModelExpressionProperties(),
                new AgentRuntimeProperties(), transport, prompts,
                readiness(true));

        assertThat(port).isInstanceOf(OpenAiCompatibleGeneralKnowledgeAdapter.class);
    }

    private AgentRuntimeReadiness readiness(boolean available) {
        AgentRuntimeReadiness readiness = mock(AgentRuntimeReadiness.class);
        when(readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE))
                .thenReturn(available);
        return readiness;
    }
}
