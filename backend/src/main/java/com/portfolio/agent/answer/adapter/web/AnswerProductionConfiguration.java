package com.portfolio.agent.answer.adapter.web;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.adapter.model.ModelExpressionProperties;
import com.portfolio.agent.answer.adapter.retrieval.RetrievalProperties;
import com.portfolio.agent.answer.service.AnswerAdmissionGate;
import com.portfolio.agent.answer.service.AnswerIdempotencyCoordinator;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnswerProductionProperties.class)
public class AnswerProductionConfiguration {

    @Bean
    ApplicationStartupDiagnostics applicationStartupDiagnostics(
            DiagnosticEventPublisher diagnosticEventPublisher,
            ModelExpressionProperties modelExpressionProperties,
            ConversationalAgentProperties conversationalAgentProperties,
            RetrievalProperties retrievalProperties,
            AnswerProductionProperties answerProductionProperties
    ) {
        return new ApplicationStartupDiagnostics(
                diagnosticEventPublisher,
                modelExpressionProperties.isEnabled(),
                conversationalAgentProperties.isEnabled(),
                retrievalProperties.getProfile().name(),
                answerProductionProperties.getRequestTimeout().toMillis(),
                answerProductionProperties.getRequestsPerMinute(),
                answerProductionProperties.getMaxConcurrent());
    }

    @Bean
    ClientAddressResolver clientAddressResolver(AnswerProductionProperties properties) {
        return new ClientAddressResolver(
                properties.isTrustProxy(), properties.getTrustedProxies());
    }

    @Bean
    AnonymousSourceHasher anonymousSourceHasher() {
        return new AnonymousSourceHasher();
    }

    @Bean
    AnswerAdmissionGate answerAdmissionGate(AnswerProductionProperties properties) {
        return new AnswerAdmissionGate(
                Clock.systemUTC(),
                properties.getRequestsPerMinute(),
                properties.getMaxConcurrent()
        );
    }

    @Bean
    AnswerIdempotencyCoordinator<ConversationAnswerResult> answerIdempotencyCoordinator(
            AnswerProductionProperties properties
    ) {
        return new AnswerIdempotencyCoordinator<>(
                Clock.systemUTC(), properties.getIdempotencyTtl());
    }

    @Bean(destroyMethod = "close")
    ExecutorService conversationRequestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ProductionConversationService productionConversationService(
            ConversationalAgentRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService conversationRequestExecutor,
            AnswerProductionProperties properties
    ) {
        return new ProductionConversationService(
                runtime,
                sourceHasher,
                admissionGate,
                idempotency,
                conversationRequestExecutor,
                properties.getRequestTimeout()
        );
    }
}
