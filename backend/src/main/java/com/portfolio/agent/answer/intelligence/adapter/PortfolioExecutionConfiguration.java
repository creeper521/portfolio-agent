package com.portfolio.agent.answer.intelligence.adapter;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.intelligence.adapter.bundle.BundlePortfolioRetriever;
import com.portfolio.agent.answer.intelligence.adapter.postgres.JdbcPostgresKnowledgeQuery;
import com.portfolio.agent.answer.intelligence.adapter.postgres.PostgresPortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.execution.adapter.bundle.BundlePortfolioCandidateRetrievalAdapter;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioCandidateRetrievalPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Production retrieval and P3 composition wiring. */
@Configuration(proxyBeanMethods = false)
public class PortfolioExecutionConfiguration {

    @Bean
    DeterministicPortfolioAnswerComposer deterministicPortfolioAnswerComposer() {
        return new DeterministicPortfolioAnswerComposer();
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

    @Bean("primaryPortfolioCandidateRetrievalPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioCandidateRetrievalPort bundlePrimaryCandidateRetrievalPort(
            @org.springframework.beans.factory.annotation.Qualifier("bundlePortfolioRetriever")
            PortfolioRetriever retriever) {
        return new BundlePortfolioCandidateRetrievalAdapter(retriever);
    }

    @Bean("fallbackPortfolioCandidateRetrievalPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioCandidateRetrievalPort bundleFallbackCandidateRetrievalPort(
            @org.springframework.beans.factory.annotation.Qualifier("bundlePortfolioRetriever")
            PortfolioRetriever retriever) {
        return new BundlePortfolioCandidateRetrievalAdapter(retriever);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetriever postgresPortfolioRetriever(
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy,
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate,
            LocalEmbeddingPort embeddingPort,
            @Qualifier("bundledPortfolioKnowledgeGateway")
            PortfolioKnowledgeGateway bundledKnowledgeGateway) {
        return new PostgresPortfolioRetriever(new JdbcPostgresKnowledgeQuery(jdbcTemplate, embeddingPort));
    }

    @Bean("bundlePortfolioRetrieverFallback")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetriever bundlePortfolioRetrieverFallback(
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy,
            @Qualifier("bundledPortfolioKnowledgeGateway") PortfolioKnowledgeGateway bundledKnowledgeGateway) {
        return new BundlePortfolioRetriever(bundledKnowledgeGateway, retrievalCoordinator, retrievalPolicy);
    }

    @Bean("primaryPortfolioCandidateRetrievalPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioCandidateRetrievalPort postgresCandidateRetrievalPort(
            @Qualifier("postgresPortfolioRetriever") PortfolioRetriever retriever) {
        return new BundlePortfolioCandidateRetrievalAdapter(retriever);
    }

    @Bean("fallbackPortfolioCandidateRetrievalPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioCandidateRetrievalPort bundleFallbackCandidateRetrievalPortForPostgres(
            @Qualifier("bundlePortfolioRetrieverFallback") PortfolioRetriever retriever) {
        return new BundlePortfolioCandidateRetrievalAdapter(retriever);
    }
}
