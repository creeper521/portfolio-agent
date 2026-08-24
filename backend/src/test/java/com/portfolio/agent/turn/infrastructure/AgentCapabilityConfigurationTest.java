package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.SystemPromptCatalog;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCapabilityConfigurationTest {

    private final AgentCapabilityConfiguration configuration =
            new AgentCapabilityConfiguration();
    private final StructuredModelTransport transport = (binding, request) -> null;
    private final SystemPromptCatalog prompts = new SystemPromptCatalog();

    @Test void promptCatalogLoadsEvenWhenModelOperationsAreDisabled() {
        SystemPromptCatalog catalog = configuration.systemPromptCatalog();

        assertThat(catalog.goalInterpretation()).isNotBlank();
        assertThat(catalog.generalKnowledge()).isNotBlank();
    }

    @Test void disabledProviderIsProjectedAsUnavailableTypedPort() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), operationPolicies(), transport, prompts,
                readiness(false));

        assertThatThrownBy(() -> port.generate(
                null, ResolvedModelExecution.none()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
    }

    @Test void enabledProviderBuildsTheRealGeneralAdapter() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), operationPolicies(), transport, prompts,
                readiness(true));

        assertThat(port).isInstanceOf(OpenAiCompatibleGeneralKnowledgeAdapter.class);
    }

    private AgentRuntimeReadiness readiness(boolean available) {
        AgentRuntimeReadiness readiness = mock(AgentRuntimeReadiness.class);
        when(readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE))
                .thenReturn(available);
        return readiness;
    }

    private ModelOperationPolicyRegistry operationPolicies() {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        OperationMode.ENABLED,
                        GeneralDraftCodec.SCHEMA_VERSION,
                        1200,
                        Duration.ofSeconds(10))));
    }
}
