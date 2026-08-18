package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;
import com.portfolio.agent.answer.service.ConversationDraftValidator;
import com.portfolio.agent.answer.service.ConversationProgressClassifier;
import com.portfolio.agent.answer.service.ConversationSubjectGuard;
import com.portfolio.agent.answer.service.ConversationWindowManager;
import com.portfolio.agent.answer.service.DynamicQuestionService;
import com.portfolio.agent.answer.service.PortfolioGroundingAssembler;
import com.portfolio.agent.answer.routing.adapter.execution.DeterministicSynthesisTaskExecutor;
import com.portfolio.agent.answer.routing.adapter.execution.GeneralSemanticTaskExecutor;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.answer.synthesis.service.CrossDomainRelationPolicy;
import com.portfolio.agent.answer.synthesis.service.CrossDomainRelationProperties;
import com.portfolio.agent.answer.synthesis.service.DeterministicCrossDomainComposer;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.OperationMode;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.turn.infrastructure.model.GoalInterpretationAdapter;
import com.portfolio.agent.turn.lifecycle.MigrationAgentTurnRuntime;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.MinimalGoalFallback;
import com.portfolio.agent.turn.planning.PortfolioReviewedGoalSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ConversationalAgentProperties.class,
        CrossDomainRelationProperties.class,
        ModelOperationProperties.class,
        GoalInterpretationProperties.class
})
public class ConversationalAgentConfiguration {

    @Bean
    ConversationalPromptFactory conversationalPromptFactory(
            ObjectMapper objectMapper
    ) throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "prompts/portfolio-agent-system.zh-CN.txt");
        return new ConversationalPromptFactory(
                objectMapper,
                resource.getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean
    OpenAiCompatibleConversationalModelAdapter conversationalModelAdapter(
            ObjectMapper objectMapper,
            ConversationalPromptFactory promptFactory,
            ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(modelProperties.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(modelProperties.getTimeout());
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory);
        return new OpenAiCompatibleConversationalModelAdapter(
                builder,
                objectMapper,
                promptFactory,
                registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                modelProperties.getMaxTokens(),
                diagnosticEventPublisher);
    }

    @Bean
    GoalInterpretationPort goalInterpretationPort(
            ObjectMapper objectMapper,
            ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry,
            GoalInterpretationProperties properties,
            DiagnosticEventPublisher diagnosticEventPublisher,
            ConversationProviderAccess providerAccess,
            ModelOperationPolicyRegistry operationPolicies) {
        if (!providerAccess.isAllowed()
                || operationPolicies.get(ModelOperation.TURN_INTERPRETATION).getMode()
                != OperationMode.ENABLED) {
            return input -> {
                throw new com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException();
            };
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        return new GoalInterpretationAdapter(
                RestClient.builder().requestFactory(requestFactory), objectMapper,
                new GoalProposalCodec(),
                registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                properties.getMaxOutputTokens(), diagnosticEventPublisher);
    }

    @Bean
    ConversationWindowManager conversationWindowManager(
            ConversationSummaryPort summaryPort,
            ConversationalAgentProperties properties
    ) {
        return new ConversationWindowManager(
                summaryPort,
                properties.getMaxInputTokens(),
                properties.getRecentRawRounds());
    }

    @Bean
    ConversationProviderAccess conversationProviderAccess(
            ConversationalAgentProperties properties,
            ModelPolicy modelPolicy,
            ModelProviderRegistrySnapshot registry
    ) {
        return new ConversationProviderAccess(
                properties.allowsProviderCalls(modelPolicy, registry));
    }

    @Bean
    PortfolioGroundingAssembler portfolioGroundingAssembler() {
        return new PortfolioGroundingAssembler(6, 12, 12000);
    }

    @Bean
    ConversationDraftValidator conversationDraftValidator(
            OpenAiCompatibleConversationalModelAdapter modelAdapter
    ) {
        return new ConversationDraftValidator(modelAdapter);
    }

    @Bean
    DynamicQuestionService dynamicQuestionService(
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            PortfolioGroundingAssembler groundingAssembler,
            ConversationalAgentProperties properties
    ) {
        return new DynamicQuestionService(
                modelAdapter,
                groundingAssembler,
                properties.getMaxSuggestedQuestions());
    }

    @Bean
    ConversationSubjectGuard conversationSubjectGuard() {
        return new ConversationSubjectGuard();
    }

    @Bean
    ConversationProgressClassifier conversationProgressClassifier() {
        return new ConversationProgressClassifier();
    }

    @Bean
    ModelOperationPolicyRegistry modelOperationPolicyRegistry(ModelOperationProperties properties) {
        return properties.toRegistry();
    }

    @Bean
    SemanticTurnEngine semanticTurnEngine(
            ConversationProviderAccess providerAccess,
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            ConversationDraftValidator draftValidator,
            PortfolioKnowledgeGateway knowledgeGateway,
            com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor portfolioTaskExecutor,
            CrossDomainRelationProperties relationProperties,
            ModelOperationPolicyRegistry operationPolicies,
            java.util.concurrent.ExecutorService conversationRequestExecutor
    ) {
        return new SemanticTurnEngine(List.of(
                portfolioTaskExecutor,
                new GeneralSemanticTaskExecutor(
                        providerAccess, modelAdapter, draftValidator, operationPolicies),
                new DeterministicSynthesisTaskExecutor(
                        relationProperties.isEnabled(),
                        new CrossDomainRelationPolicy(),
                        new DeterministicCrossDomainComposer(),
                        new com.portfolio.agent.answer.synthesis.service.CrossDomainExpressionPipeline(
                                modelAdapter, operationPolicies))),
                conversationRequestExecutor, 4);
    }

    @Bean
    MigrationAgentTurnRuntime conversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            GoalInterpretationPort goalInterpretationPort,
            SemanticTurnEngine semanticTurnEngine,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        GoalResolver goalResolver = new GoalResolver(
                goalInterpretationPort,
                new PortfolioReviewedGoalSource(knowledgeGateway),
                new GoalInterpretationInputFactory(),
                new MinimalGoalFallback(),
                new GoalBoundaryPolicy());
        com.portfolio.agent.turn.planning.SemanticPlanCompiler compiler =
                new com.portfolio.agent.turn.planning.SemanticPlanCompiler(
                        new com.portfolio.agent.turn.planning.SemanticPlanValidator());
        return new MigrationAgentTurnRuntime(
                knowledgeGateway, goalResolver, compiler, semanticTurnEngine);
    }

}
