package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.configuration.ConversationalAgentProperties;
import com.portfolio.agent.infrastructure.model.configuration.GoalInterpretationProperties;
import com.portfolio.agent.infrastructure.model.configuration.ModelExpressionProperties;
import com.portfolio.agent.infrastructure.model.configuration.ModelOperationProperties;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderRegistrySnapshot;
import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import com.portfolio.agent.infrastructure.model.policy.ModelPolicy;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.turn.api.AgentRequestAdmissionGate;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeGenerator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;
import com.portfolio.agent.turn.capability.general.GeneralPresentationComposer;
import com.portfolio.agent.turn.capability.general.GeneralTaskExecutor;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentationComposer;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainTaskExecutor;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.infrastructure.model.GoalInterpretationAdapter;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.OpenAiCompatibleStructuredModelTransport;
import com.portfolio.agent.infrastructure.model.SystemPromptCatalog;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.lifecycle.ActiveTurnCapacity;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintFactory;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.SafeConversationalFastPath;
import com.portfolio.agent.turn.planning.SemanticRouteValidator;
import com.portfolio.agent.turn.planning.PortfolioReviewedGoalSource;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ConversationalAgentProperties.class,
        ModelOperationProperties.class,
        GoalInterpretationProperties.class,
        AgentRuntimeProperties.class,
        com.portfolio.agent.turn.state.configuration.ConversationContextProperties.class
})
public class AgentCapabilityConfiguration {
    @Bean
    SystemPromptCatalog systemPromptCatalog() {
        return new SystemPromptCatalog();
    }

    @Bean
    ApplicationStartupDiagnostics applicationStartupDiagnostics(
            DiagnosticEventPublisher diagnosticEventPublisher,
            ModelExpressionProperties modelExpressionProperties,
            ConversationalAgentProperties conversationalAgentProperties,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.retrieval.profile:DISABLED}") String retrievalProfile,
            AgentRuntimeProperties runtimeProperties) {
        return new ApplicationStartupDiagnostics(
                diagnosticEventPublisher,
                modelExpressionProperties.isEnabled(),
                conversationalAgentProperties.isEnabled(),
                retrievalProfile,
                runtimeProperties.getTurnTimeout().toMillis(),
                runtimeProperties.getRequestsPerMinute(),
                runtimeProperties.getMaxConcurrentPerSource());
    }

    @Bean(destroyMethod = "close")
    ExecutorService conversationRequestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ClientAddressResolver clientAddressResolver(AgentRuntimeProperties properties) {
        return new ClientAddressResolver(
                properties.isTrustProxy(), properties.getTrustedProxies());
    }

    @Bean
    AnonymousSourceHasher anonymousSourceHasher() {
        return new AnonymousSourceHasher();
    }

    @Bean
    AgentRequestAdmissionGate agentRequestAdmissionGate(AgentRuntimeProperties properties) {
        return new AgentRequestAdmissionGate(
                java.time.Clock.systemUTC(),
                properties.getRequestsPerMinute(),
                properties.getMaxConcurrentPerSource(),
                properties.getMaxTrackedSources());
    }

    @Bean
    ActiveTurnCapacity activeTurnCapacity(AgentRuntimeProperties properties) {
        return new ActiveTurnCapacity(properties.getMaxActiveTurns());
    }

    @Bean
    ConversationProviderAccess conversationProviderAccess(
            ConversationalAgentProperties properties,
            ModelPolicy modelPolicy,
            ModelProviderRegistrySnapshot registry) {
        return new ConversationProviderAccess(properties.allowsProviderCalls(modelPolicy, registry));
    }

    @Bean
    ModelOperationPolicyRegistry modelOperationPolicyRegistry(ModelOperationProperties properties) {
        return properties.toRegistry();
    }

    @Bean
    AgentRuntimeReadiness agentRuntimeReadiness(
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties
                    contextProperties,
            ConversationProviderAccess providerAccess,
            ModelOperationPolicyRegistry operationPolicies,
            ModelPolicy modelPolicy) {
        return new AgentRuntimeReadiness(
                contextProperties.getMode(), providerAccess,
                operationPolicies, modelPolicy.getProvider());
    }

    @Bean
    StructuredModelTransport structuredModelTransport(
            ObjectMapper mapper, ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry,
            AgentRuntimeProperties runtimeProperties,
            DiagnosticEventPublisher diagnostics) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newBuilder()
                        .connectTimeout(runtimeProperties.getGeneralKnowledgeTimeout())
                        .build(),
                mapper, registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                runtimeProperties.getGeneralKnowledgeTimeout(), diagnostics);
    }

    @Bean
    GoalInterpretationPort goalInterpretationPort(
            ObjectMapper objectMapper,
            GoalInterpretationProperties properties,
            AgentRuntimeProperties runtimeProperties,
            StructuredModelTransport transport,
            SystemPromptCatalog prompts,
            AgentRuntimeReadiness readiness,
            DiagnosticEventPublisher diagnostics) {
        if (!readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION)) {
            return (input, deadline) -> { throw new GoalInterpretationUnavailableException(); };
        }
        return new GoalInterpretationAdapter(
                transport, objectMapper, new GoalProposalCodec(),
                prompts.goalInterpretation(),
                properties.getMaxOutputTokens(), runtimeProperties.getGoalInterpretationTimeout(),
                new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics));
    }

    @Bean
    GeneralKnowledgeModelPort generalKnowledgeModelPort(
            ObjectMapper objectMapper,
            ModelExpressionProperties modelProperties,
            AgentRuntimeProperties runtimeProperties,
            StructuredModelTransport transport,
            SystemPromptCatalog prompts,
            AgentRuntimeReadiness readiness) {
        if (!readiness.isOperationAvailable(ModelOperation.GENERAL_KNOWLEDGE)) {
            return request -> {
                throw new GeneralKnowledgeUnavailableException(
                        "general capability is unavailable");
            };
        }
        return new OpenAiCompatibleGeneralKnowledgeAdapter(
                transport, objectMapper, prompts.generalKnowledge(),
                modelProperties.getMaxTokens(),
                runtimeProperties.getGeneralKnowledgeTimeout());
    }

    @Bean
    GeneralTaskExecutor generalTaskExecutor(
            ObjectMapper objectMapper,
            GeneralKnowledgeModelPort modelPort,
            DiagnosticEventPublisher diagnostics) {
        return new GeneralTaskExecutor(
                new GeneralKnowledgeGenerator(
                        modelPort, new GeneralDraftCodec(objectMapper),
                        new GeneralDraftValidator(),
                        new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics)),
                new GeneralPresentationComposer());
    }

    @Bean
    CrossDomainTaskExecutor crossDomainTaskExecutor() {
        return new CrossDomainTaskExecutor(new CrossDomainPresentationComposer());
    }

    @Bean
    SemanticTurnEngine semanticTurnEngine(
            com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor portfolioTaskExecutor,
            GeneralTaskExecutor generalTaskExecutor,
            CrossDomainTaskExecutor crossDomainTaskExecutor,
            java.util.concurrent.ExecutorService conversationRequestExecutor) {
        return new SemanticTurnEngine(
                List.of(portfolioTaskExecutor, generalTaskExecutor, crossDomainTaskExecutor),
                conversationRequestExecutor, 4);
    }

    @Bean
    GoalResolver goalResolver(
            PortfolioKnowledgeGateway knowledgeGateway,
            GoalInterpretationPort goalInterpretationPort,
            DiagnosticEventPublisher diagnostics) {
        return new GoalResolver(
                goalInterpretationPort, new PortfolioReviewedGoalSource(knowledgeGateway),
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(), new SemanticRouteValidator(),
                new GoalBoundaryPolicy(),
                new com.portfolio.agent.common.observability.ModelOutputDiagnostics(diagnostics));
    }

    @Bean SemanticPlanCompiler semanticPlanCompiler() {
        return new SemanticPlanCompiler(new SemanticPlanValidator());
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode", havingValue = "IN_MEMORY")
    AgentStateStore inMemoryTurnExecutionStore(
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties properties,
            InMemoryConversationSessionStore sessionStore) {
        properties.validate();
        java.time.Clock clock = java.time.Clock.systemUTC();
        return new com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, properties.getClarificationTtl()),
                properties.getAbsoluteTtl(), sessionStore, clock);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "DISABLED", matchIfMissing = true)
    AgentStateStore unavailableTurnExecutionStore() {
        return new com.portfolio.agent.turn.state.UnavailableTurnExecutionStore();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "IN_MEMORY")
    InMemoryConversationSessionStore inMemoryConversationSessionStore() {
        return new InMemoryConversationSessionStore();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode",
            havingValue = "DISABLED", matchIfMissing = true)
    ConversationSessionStore unavailableConversationSessionStore() {
        return new InMemoryConversationSessionStore();
    }

    @Bean
    AgentTurnLifecycleService agentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine,
            AgentStateStore store, ConversationSessionStore sessionStore,
            com.portfolio.agent.turn.state.configuration.ConversationContextProperties properties,
            AgentRuntimeProperties runtimeProperties,
            ExecutorService conversationRequestExecutor) {
        byte[] configuredTokenKey = decodeOrRandom(properties.getCrypto().getCurrentTokenKey());
        java.util.List<byte[]> previousTokenKeys =
                properties.getCrypto().getPreviousTokenKey() == null
                        || properties.getCrypto().getPreviousTokenKey().isBlank()
                        ? java.util.List.of()
                        : java.util.List.of(decodeConfigured(
                        properties.getCrypto().getPreviousTokenKey()));
        byte[] fingerprintSecret = configuredTokenKey;
        byte[] sessionSecret = configuredTokenKey;
        String fingerprintKeyId = properties.getCrypto().getCurrentTokenKeyId() == null
                || properties.getCrypto().getCurrentTokenKeyId().isBlank()
                ? "ephemeral-token" : properties.getCrypto().getCurrentTokenKeyId().trim();
        java.util.Map<String, byte[]> previousFingerprintKeys =
                previousTokenKeys.isEmpty() ? java.util.Map.of()
                        : java.util.Map.of(
                        properties.getCrypto().getPreviousTokenKeyId().trim(),
                        previousTokenKeys.getFirst());
        java.time.Clock clock = java.time.Clock.systemUTC();
        ContextMutationPlanner planner = new ContextMutationPlanner(() -> {
            byte[] value = new byte[24];
            new java.security.SecureRandom().nextBytes(value);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        });
        return new AgentTurnLifecycleService(
                knowledgeGateway, goalResolver, compiler, engine,
                new PublicAgentTurnProjector(), planner, store,
                new RequestFingerprintFactory(
                        fingerprintKeyId, fingerprintSecret, previousFingerprintKeys),
                new ConversationSessionResolver(
                        sessionStore, sessionSecret, previousTokenKeys,
                        clock, properties.getAbsoluteTtl()),
                conversationRequestExecutor,
                clock, runtimeProperties.getLeaseDuration(),
                runtimeProperties.getTurnTimeout(),
                runtimeProperties.getSettlementReserve(),
                properties.getDiscussionTtl());
    }

    private byte[] randomSecret() {
        byte[] value = new byte[32];
        new java.security.SecureRandom().nextBytes(value);
        return value;
    }

    private byte[] decodeOrRandom(String encoded) {
        if (encoded == null || encoded.isBlank()) return randomSecret();
        try {
            byte[] value = java.util.Base64.getDecoder().decode(encoded.trim());
            if (value.length < 32) throw new IllegalStateException("Agent token key is too short");
            return value;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Agent token key must be base64", failure);
        }
    }

    private byte[] decodeConfigured(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Agent token key is required");
        }
        return decodeOrRandom(encoded);
    }
}
