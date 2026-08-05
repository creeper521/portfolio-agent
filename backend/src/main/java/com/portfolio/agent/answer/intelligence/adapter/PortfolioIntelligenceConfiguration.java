package com.portfolio.agent.answer.intelligence.adapter;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.engine.QuestionNormalizer;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.adapter.bundle.BundlePortfolioRetriever;
import com.portfolio.agent.answer.intelligence.adapter.postgres.JdbcPostgresKnowledgeQuery;
import com.portfolio.agent.answer.intelligence.adapter.postgres.PostgresPortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;
import com.portfolio.agent.answer.intelligence.service.DefaultPortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioRecommendationPolicy;
import com.portfolio.agent.answer.intelligence.service.PortfolioPresetResolver;
import com.portfolio.agent.answer.intelligence.service.PortfolioReferenceContextValidator;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskResolver;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskValidator;
import com.portfolio.agent.answer.intelligence.service.RecommendationBatchFingerprint;
import com.portfolio.agent.answer.intelligence.service.RecommendationContextValidator;
import com.portfolio.agent.answer.intelligence.service.StructuredSubjectResolver;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.PortfolioIntelligenceAnswerAssembler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class PortfolioIntelligenceConfiguration {

    @Bean
    PortfolioTaskResolver portfolioTaskResolver(
            PortfolioTaskClassifierPort classifier,
            ConversationalAgentProperties properties) {
        return new PortfolioTaskResolver(
                classifier, properties.getMinimumPortfolioTaskConfidence());
    }

    @Bean
    PortfolioTaskValidator portfolioTaskValidator() {
        return new PortfolioTaskValidator();
    }

    @Bean
    PortfolioPresetResolver portfolioPresetResolver(QuestionNormalizer normalizer) {
        return new PortfolioPresetResolver(normalizer);
    }

    @Bean
    PortfolioReferenceContextValidator portfolioReferenceContextValidator() {
        return new PortfolioReferenceContextValidator();
    }

    @Bean
    StructuredSubjectResolver structuredSubjectResolver() {
        return new StructuredSubjectResolver();
    }

    @Bean
    PortfolioRecommendationPolicy portfolioRecommendationPolicy() {
        return new PortfolioRecommendationPolicy();
    }

    @Bean
    RecommendationBatchFingerprint recommendationBatchFingerprint() {
        return new RecommendationBatchFingerprint();
    }

    @Bean
    RecommendationContextValidator recommendationContextValidator(
            RecommendationBatchFingerprint fingerprint) {
        return new RecommendationContextValidator(fingerprint);
    }

    @Bean
    PortfolioIntelligence portfolioIntelligence(
            PortfolioTaskValidator taskValidator,
            PortfolioRetriever retriever,
            PortfolioRecommendationPolicy recommendationPolicy,
            RecommendationContextValidator contextValidator,
            PortfolioKnowledgeGateway knowledgeGateway,
            PortfolioPresetResolver presetResolver,
            PortfolioReferenceContextValidator referenceContextValidator,
            PortfolioTaskResolver taskResolver,
            StructuredSubjectResolver structuredSubjectResolver,
            ConversationProviderAccess providerAccess) {
        return new DefaultPortfolioIntelligence(
                taskValidator, retriever, recommendationPolicy, contextValidator,
                knowledgeGateway, presetResolver, referenceContextValidator,
                taskResolver, structuredSubjectResolver, providerAccess);
    }

    @Bean
    PortfolioIntelligenceAnswerAssembler portfolioIntelligenceAnswerAssembler() {
        return new PortfolioIntelligenceAnswerAssembler();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioRetriever bundlePortfolioRetriever(
            PortfolioKnowledgeGateway knowledgeGateway,
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy) {
        return new BundlePortfolioRetriever(knowledgeGateway, retrievalCoordinator, retrievalPolicy);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetriever failoverPortfolioRetriever(
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy,
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate,
            LocalEmbeddingPort embeddingPort,
            @Qualifier("bundledPortfolioKnowledgeGateway")
            PortfolioKnowledgeGateway bundledKnowledgeGateway) {
        PortfolioRetriever bundleRetriever = new BundlePortfolioRetriever(
                bundledKnowledgeGateway, retrievalCoordinator, retrievalPolicy);
        PortfolioRetriever postgresRetriever = new PostgresPortfolioRetriever(
                new JdbcPostgresKnowledgeQuery(jdbcTemplate, embeddingPort));
        return new FailoverPortfolioRetriever(postgresRetriever, bundleRetriever);
    }
}
