package com.portfolio.agent.turn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.adapter.model.GoalInterpretationProperties;
import com.portfolio.agent.answer.adapter.model.ModelExpressionProperties;
import com.portfolio.agent.answer.adapter.model.ModelOperationProperties;
import com.portfolio.agent.answer.adapter.model.ModelProviderRegistrySnapshot;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicy;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.OperationMode;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeGenerator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralPresentationComposer;
import com.portfolio.agent.turn.capability.general.GeneralTaskExecutor;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentationComposer;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainTaskExecutor;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.infrastructure.model.GoalInterpretationAdapter;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
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
import com.portfolio.agent.turn.planning.MinimalGoalFallback;
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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ConversationalAgentProperties.class,
        ModelOperationProperties.class,
        GoalInterpretationProperties.class,
        com.portfolio.agent.answer.context.adapter.postgres.ConversationContextProperties.class
})
public class AgentCapabilityConfiguration {
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
    GoalInterpretationPort goalInterpretationPort(
            ObjectMapper objectMapper,
            ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry,
            GoalInterpretationProperties properties,
            DiagnosticEventPublisher diagnostics,
            ConversationProviderAccess providerAccess,
            ModelOperationPolicyRegistry operationPolicies) {
        if (!providerAccess.isAllowed()
                || operationPolicies.get(ModelOperation.TURN_INTERPRETATION).getMode() != OperationMode.ENABLED) {
            return input -> { throw new GoalInterpretationUnavailableException(); };
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        return new GoalInterpretationAdapter(
                RestClient.builder().requestFactory(requestFactory), objectMapper,
                new GoalProposalCodec(), registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                properties.getMaxOutputTokens(), diagnostics);
    }

    @Bean
    GeneralKnowledgeModelPort generalKnowledgeModelPort(
            ObjectMapper objectMapper,
            ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry,
            ModelOperationPolicyRegistry operationPolicies) {
        ModelOperationPolicy policy = operationPolicies.get(ModelOperation.GENERAL_KNOWLEDGE);
        Duration timeout = policy.getTimeout() == null ? Duration.ofSeconds(3) : policy.getTimeout();
        return new OpenAiCompatibleGeneralKnowledgeAdapter(
                HttpClient.newBuilder().connectTimeout(timeout).build(), objectMapper,
                registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                modelProperties.getMaxTokens(), timeout);
    }

    @Bean
    GeneralTaskExecutor generalTaskExecutor(
            ObjectMapper objectMapper,
            ConversationProviderAccess providerAccess,
            ModelOperationPolicyRegistry operationPolicies,
            GeneralKnowledgeModelPort modelPort) {
        return new GeneralTaskExecutor(
                new GeneralKnowledgeGenerator(
                        providerAccess, operationPolicies, modelPort,
                        new GeneralDraftCodec(objectMapper), new GeneralDraftValidator()),
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
            GoalInterpretationPort goalInterpretationPort) {
        return new GoalResolver(
                goalInterpretationPort, new PortfolioReviewedGoalSource(knowledgeGateway),
                new GoalInterpretationInputFactory(), new MinimalGoalFallback(), new GoalBoundaryPolicy());
    }

    @Bean SemanticPlanCompiler semanticPlanCompiler() {
        return new SemanticPlanCompiler(new SemanticPlanValidator());
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "portfolio.conversation-context", name = "mode", havingValue = "IN_MEMORY")
    AgentStateStore inMemoryTurnExecutionStore() {
        return new com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore();
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
    ConversationSessionStore inMemoryConversationSessionStore() {
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
            com.portfolio.agent.answer.context.adapter.postgres.ConversationContextProperties properties) {
        byte[] configuredTokenKey = decodeOrRandom(properties.getCrypto().getCurrentTokenKey());
        byte[] fingerprintSecret = configuredTokenKey;
        byte[] sessionSecret = configuredTokenKey;
        java.time.Clock clock = java.time.Clock.systemUTC();
        ContextMutationPlanner planner = new ContextMutationPlanner(() -> {
            byte[] value = new byte[24];
            new java.security.SecureRandom().nextBytes(value);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        });
        return new AgentTurnLifecycleService(
                knowledgeGateway, goalResolver, compiler, engine,
                new PublicAgentTurnProjector(), planner, store,
                new RequestFingerprintFactory(fingerprintSecret),
                new ConversationSessionResolver(
                        sessionStore, sessionSecret,
                        clock, java.time.Duration.ofMinutes(30)),
                clock, java.time.Duration.ofSeconds(30),
                java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(30));
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
}
