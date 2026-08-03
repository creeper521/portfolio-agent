package com.portfolio.agent.answer.intelligence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
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
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskResolver;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskValidator;
import com.portfolio.agent.answer.intelligence.service.RecommendationBatchFingerprint;
import com.portfolio.agent.answer.intelligence.service.RecommendationContextValidator;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.PortfolioIntelligenceAnswerAssembler;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
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
            RecommendationContextValidator contextValidator) {
        return new DefaultPortfolioIntelligence(
                taskValidator, retriever, recommendationPolicy, contextValidator);
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
            ObjectMapper objectMapper,
            @Value("classpath:public-data/bundle/manifest.json") Resource manifest,
            @Value("classpath:public-data/bundle/portfolio.json") Resource portfolio,
            @Value("classpath:public-data/bundle/presentation.json") Resource presentation,
            @Value("classpath:public-data/bundle/rag-documents.jsonl") Resource ragDocuments,
            @Value("classpath:public-data/bundle/keyword-index.json") Resource keywordIndex,
            @Value("classpath:public-data/bundle/vector-index.bin") Resource vectorIndex,
            @Value("classpath:public-data/bundle/checksums.json") Resource checksums,
            PortfolioSnapshotValidator validator,
            ApplicationStartupDiagnostics startupDiagnostics) {
        JsonPublicPortfolioRepository bundledRepository = new JsonPublicPortfolioRepository(
                objectMapper,
                manifest,
                portfolio,
                presentation,
                ragDocuments,
                keywordIndex,
                vectorIndex,
                checksums,
                "",
                validator,
                startupDiagnostics);
        PortfolioKnowledgeGateway bundledKnowledgeGateway =
                new LocalPortfolioKnowledgeAdapter(bundledRepository);
        PortfolioRetriever bundleRetriever = new BundlePortfolioRetriever(
                bundledKnowledgeGateway, retrievalCoordinator, retrievalPolicy);
        PortfolioRetriever postgresRetriever = new PostgresPortfolioRetriever(
                new JdbcPostgresKnowledgeQuery(jdbcTemplate, embeddingPort));
        return new FailoverPortfolioRetriever(postgresRetriever, bundleRetriever);
    }
}
