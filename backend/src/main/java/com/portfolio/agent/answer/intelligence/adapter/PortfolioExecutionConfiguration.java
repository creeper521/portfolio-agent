package com.portfolio.agent.answer.intelligence.adapter;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.intelligence.adapter.bundle.BundlePortfolioRetriever;
import com.portfolio.agent.answer.intelligence.adapter.postgres.JdbcPostgresKnowledgeQuery;
import com.portfolio.agent.answer.intelligence.adapter.postgres.PostgresPortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.PortfolioInvocationFactory;
import com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor;
import com.portfolio.agent.turn.capability.portfolio.evidence.EvidencePromotionValidator;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentationComposer;
import com.portfolio.agent.turn.capability.portfolio.presentation.PresentationPolicy;
import com.portfolio.agent.turn.capability.portfolio.retrieval.BundlePortfolioRetrieverAdapter;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioRetrieverPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PostgresPortfolioRetrieverAdapter;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSupportEvaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Production retrieval and P3 composition wiring. */
@Configuration(proxyBeanMethods = false)
public class PortfolioExecutionConfiguration {

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

    @Bean("bundlePortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioRetrieverPort bundlePortfolioRetrieverPort(
            @org.springframework.beans.factory.annotation.Qualifier("bundlePortfolioRetriever")
            PortfolioRetriever retriever) {
        return new BundlePortfolioRetrieverAdapter(retriever);
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

    @Bean("postgresPortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetrieverPort postgresPortfolioRetrieverPort(
            @Qualifier("postgresPortfolioRetriever") PortfolioRetriever retriever) {
        return new PostgresPortfolioRetrieverAdapter(retriever);
    }

    @Bean("bundlePortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetrieverPort bundlePortfolioRetrieverPortForPostgres(
            @Qualifier("bundlePortfolioRetrieverFallback") PortfolioRetriever retriever) {
        return new BundlePortfolioRetrieverAdapter(retriever);
    }

    @Bean("portfolioEvidenceCapabilityV2")
    @ConditionalOnProperty(prefix = "portfolio.database.public", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    PortfolioEvidenceCapability bundlePortfolioEvidenceCapability(
            @Qualifier("bundlePortfolioRetrieverPort") PortfolioRetrieverPort bundle) {
        return new PortfolioEvidenceCapability(
                java.util.Map.of(com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend.BUNDLE, bundle),
                new RetrievalFallbackPolicy(), new EvidencePromotionValidator(java.time.Clock.systemUTC()));
    }

    @Bean("portfolioEvidenceCapabilityV2")
    @ConditionalOnProperty(prefix = "portfolio.database.public", name = "enabled", havingValue = "true")
    PortfolioEvidenceCapability postgresPortfolioEvidenceCapability(
            @Qualifier("postgresPortfolioRetrieverPort") PortfolioRetrieverPort postgres,
            @Qualifier("bundlePortfolioRetrieverPort") PortfolioRetrieverPort bundle) {
        return new PortfolioEvidenceCapability(
                java.util.Map.of(
                        com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend.POSTGRESQL, postgres,
                        com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend.BUNDLE, bundle),
                new RetrievalFallbackPolicy(), new EvidencePromotionValidator(java.time.Clock.systemUTC()));
    }

    @Bean
    PortfolioTaskExecutor portfolioTaskExecutor(
            @Qualifier("portfolioEvidenceCapabilityV2") PortfolioEvidenceCapability capability,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.database.public.enabled:false}") boolean databaseEnabled) {
        return new PortfolioTaskExecutor(
                new PortfolioInvocationFactory(databaseEnabled
                        ? com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend.POSTGRESQL
                        : com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend.BUNDLE),
                capability,
                new PortfolioSemanticResultFactory(new PortfolioSupportEvaluator()),
                new PortfolioPresentationComposer(PresentationPolicy.defaults()));
    }
}
