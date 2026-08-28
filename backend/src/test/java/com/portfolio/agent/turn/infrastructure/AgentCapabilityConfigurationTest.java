package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.infrastructure.model.SystemPromptCatalog;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.turn.planning.UnresolvedIntentPolicy;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
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

    @Test void unresolvedIntentPolicyIsWiredAsItsOwnDeterministicBean() {
        assertThat(configuration.unresolvedIntentPolicy())
                .isInstanceOf(UnresolvedIntentPolicy.class);
    }

    @Test void disabledProviderIsProjectedAsUnavailableTypedPort() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), operationPolicies(),
                StructuredModelTestFixtures.gateway(transport), prompts,
                readiness(false), event -> { });

        assertThatThrownBy(() -> port.generate(
                null, ResolvedModelExecution.none()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
    }

    @Test void enabledProviderBuildsTheRealGeneralAdapter() {
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), operationPolicies(),
                StructuredModelTestFixtures.gateway(transport), prompts,
                readiness(true), event -> { });

        assertThat(port).isInstanceOf(OpenAiCompatibleGeneralKnowledgeAdapter.class);
    }

    @Test
    void defaultTenSecondGeneralAssemblyCanReachASecondNoResponseAttempt() {
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        List<ProviderAttemptContext> contexts = new ArrayList<>();
        List<StructuredModelRequest> requests = new ArrayList<>();
        StructurallyValidatedOutput output = StructuredModelTestFixtures
                .validatedGeneral("""
                        {"topic":"并发控制","statements":[
                          {"role":"DEFINITION","text":"定义。",
                           "subject":null,"dimension":null,
                           "aspects":["DEFINITION"]},
                          {"role":"MECHANISM","text":"机制。",
                           "subject":null,"dimension":null,
                           "aspects":["MECHANISM"]}],"caveats":[]}
                        """);
        when(gateway.execute(
                any(), any(), any(), any(ProviderAttemptContext.class)))
                .thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(1));
                    ProviderAttemptContext context = invocation.getArgument(3);
                    contexts.add(context);
                    if (context.attemptIndex() == 1) {
                        throw StructuredModelFailure.deadline(
                                StructuredModelFailure.TimeoutDisposition
                                        .NO_RESPONSE,
                                null);
                    }
                    return output;
                });
        GeneralKnowledgeModelPort port = configuration.generalKnowledgeModelPort(
                new ObjectMapper(), operationPolicies(), gateway, prompts,
                readiness(true), event -> { });

        assertThat(port.generate(
                GeneralKnowledgeRequest.explanation(
                        "并发控制", UserGoalProposal.Depth.CONCISE,
                        GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                        TurnDeadline.after(
                                Duration.ofSeconds(12), Clock.systemUTC())),
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.qwenV7ToolBindings())))
                .isSameAs(output);

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).isSameAs(requests.get(0));
        assertThat(requests.get(0).deadline().remainingMillis())
                .isPositive().isLessThanOrEqualTo(10_000L);
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0).attemptTimeoutCap())
                .hasValueSatisfying(cap -> assertThat(cap)
                        .isGreaterThan(Duration.ofSeconds(6))
                        .isLessThanOrEqualTo(Duration.ofMillis(6_750)));
        assertThat(contexts.get(1).attemptTimeoutCap()).isEmpty();
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
