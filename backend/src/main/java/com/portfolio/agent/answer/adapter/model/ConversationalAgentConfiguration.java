package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import com.portfolio.agent.answer.service.ConversationDraftValidator;
import com.portfolio.agent.answer.service.ConversationProgressClassifier;
import com.portfolio.agent.answer.service.ConversationSubjectGuard;
import com.portfolio.agent.answer.service.ConversationToolService;
import com.portfolio.agent.answer.service.ConversationWindowManager;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import com.portfolio.agent.answer.service.DeterministicConversationFallback;
import com.portfolio.agent.answer.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.answer.service.DynamicQuestionService;
import com.portfolio.agent.answer.service.PortfolioGroundingAssembler;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.mapper.SemanticTurnRequestMapper;
import com.portfolio.agent.answer.routing.adapter.crypto.JdkPlanCryptographyAdapter;
import com.portfolio.agent.answer.routing.adapter.crypto.PlanCryptographyPort;
import com.portfolio.agent.answer.routing.adapter.execution.DeterministicSynthesisTaskExecutor;
import com.portfolio.agent.answer.routing.adapter.execution.GeneralSemanticTaskExecutor;
import com.portfolio.agent.answer.routing.adapter.execution.PortfolioSemanticTaskExecutor;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.service.DefaultTurnRouter;
import com.portfolio.agent.answer.routing.service.GlobalBoundaryGate;
import com.portfolio.agent.answer.routing.service.LegacySemanticContextAdapter;
import com.portfolio.agent.answer.routing.service.PlanConfirmationService;
import com.portfolio.agent.answer.routing.service.PlanFingerprintService;
import com.portfolio.agent.answer.routing.service.RoutingContextResolver;
import com.portfolio.agent.answer.routing.service.SemanticPlanCompiler;
import com.portfolio.agent.answer.routing.service.SemanticPlanValidator;
import com.portfolio.agent.answer.routing.service.SemanticRoutingPolicy;
import com.portfolio.agent.answer.routing.service.SemanticSignalCollector;
import com.portfolio.agent.answer.routing.service.SemanticTurnCoordinator;
import com.portfolio.agent.answer.routing.service.TurnDecisionPolicy;
import com.portfolio.agent.answer.routing.service.TurnRouter;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationalAgentProperties.class)
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
    ConversationToolService conversationToolService(
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            PublicKnowledgeTools tools,
            ConversationalAgentProperties properties,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        return new ConversationToolService(
                modelAdapter,
                tools,
                properties.getMaxToolRounds(),
                properties.getMaxToolCalls(),
                diagnosticEventPublisher);
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
    DeterministicConversationFallback deterministicConversationFallback() {
        return new DeterministicConversationFallback();
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
    TurnRouter semanticTurnRouter(
            PortfolioKnowledgeGateway knowledgeGateway,
            SemanticPlanValidator semanticPlanValidator,
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            ConversationProviderAccess providerAccess,
            ConversationalAgentProperties properties
    ) {
        return DefaultTurnRouter.fromPublicSubjects(
                publicSubjects(knowledgeGateway),
                new GlobalBoundaryGate(),
                new RoutingContextResolver(new LegacySemanticContextAdapter()),
                new SemanticSignalCollector(),
                new SemanticPlanCompiler(new SemanticRoutingPolicy()),
                semanticPlanValidator,
                new TurnDecisionPolicy(),
                modelAdapter,
                properties.isSemanticClassifierEnabled() && providerAccess.isAllowed());
    }

    @Bean
    SemanticPlanValidator semanticPlanValidator() {
        return new SemanticPlanValidator(new PlanFingerprintService());
    }

    @Bean
    PlanConfirmationService planConfirmationService(
            ConversationalAgentProperties properties,
            SemanticPlanValidator semanticPlanValidator
    ) {
        return new PlanConfirmationService(
                confirmationCryptography(properties), semanticPlanValidator, Clock.systemUTC());
    }

    @Bean
    SemanticTurnCoordinator semanticTurnCoordinator(
            PortfolioIntelligence portfolioIntelligence,
            ConversationProviderAccess providerAccess,
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            ConversationDraftValidator draftValidator,
            PortfolioKnowledgeGateway knowledgeGateway,
            DeterministicPortfolioAnswerComposer answerComposer
    ) {
        String contentVersion = knowledgeGateway.getContent().getContentVersion();
        return new SemanticTurnCoordinator(List.of(
                new PortfolioSemanticTaskExecutor(
                        portfolioIntelligence, ignored -> Optional.empty(), contentVersion, answerComposer),
                new GeneralSemanticTaskExecutor(providerAccess, modelAdapter, draftValidator),
                new DeterministicSynthesisTaskExecutor()));
    }

    @Bean
    ConversationalAgentRuntime conversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            SemanticTurnRequestMapper requestMapper,
            TurnRouter semanticTurnRouter,
            PlanConfirmationService planConfirmationService,
            SemanticTurnCoordinator semanticTurnCoordinator,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        return new ConversationalAgentRuntime(
                knowledgeGateway,
                requestMapper,
                semanticTurnRouter,
                planConfirmationService,
                semanticTurnCoordinator,
                decisionPublisher,
                diagnosticEventPublisher);
    }

    private List<DefaultTurnRouter.PublicSubjectSpec> publicSubjects(
            PortfolioKnowledgeGateway knowledgeGateway) {
        List<DefaultTurnRouter.PublicSubjectSpec> subjects = new ArrayList<>();
        String contentVersion = knowledgeGateway.getContent().getContentVersion();
        addPublicSubjects(
                subjects, knowledgeGateway.getContent().getProjects(), SubjectType.PROJECT, contentVersion);
        addPublicSubjects(subjects, knowledgeGateway.getContent().getCases(), SubjectType.CASE, contentVersion);
        return List.copyOf(subjects);
    }

    private void addPublicSubjects(
            List<DefaultTurnRouter.PublicSubjectSpec> target,
            List<AnswerKnowledge> knowledge,
            SubjectType subjectType,
            String contentVersion) {
        for (AnswerKnowledge item : knowledge) {
            if (!matches(subjectType, item.getSubjectType())) {
                continue;
            }
            Set<String> aliases = new java.util.LinkedHashSet<>();
            addAlias(aliases, item.getStableId());
            addAlias(aliases, item.getSlug());
            addAlias(aliases, item.getTitle());
            target.add(new DefaultTurnRouter.PublicSubjectSpec(
                    subjectType, item.getStableId(), contentVersion, item.getTitle(), aliases));
        }
    }

    private boolean matches(SubjectType subjectType, AnswerSubjectType sourceType) {
        return (subjectType == SubjectType.PROJECT && sourceType == AnswerSubjectType.PROJECT)
                || (subjectType == SubjectType.CASE && sourceType == AnswerSubjectType.CASE);
    }

    private void addAlias(Set<String> aliases, String value) {
        if (value != null && !value.isBlank()) {
            aliases.add(value.trim());
        }
    }

    private PlanCryptographyPort confirmationCryptography(ConversationalAgentProperties properties) {
        try {
            return JdkPlanCryptographyAdapter.fromBase64(
                    properties.getPlanConfirmationEncryptionKey(),
                    properties.getPlanConfirmationIntegrityKey());
        } catch (IllegalArgumentException exception) {
            return new PlanCryptographyPort() {
                @Override
                public SealedPlan seal(
                        com.portfolio.agent.answer.routing.service.ValidatedSemanticTurnPlan plan,
                        PlanConfirmation.Identity identity,
                        PlanConfirmation.VersionBinding versionBinding) {
                    throw new IllegalStateException("plan confirmation is unavailable");
                }

                @Override
                public boolean isIntegrityValid(PlanConfirmation.Submission submission) {
                    return false;
                }

                @Override
                public OpenedPlan open(PlanConfirmation.Submission submission) {
                    throw new IllegalArgumentException("plan confirmation is invalid");
                }
            };
        }
    }

}
