package com.portfolio.agent.turn.infrastructure;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.PortfolioInvocationFactory;
import com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor;
import com.portfolio.agent.turn.capability.portfolio.evidence.EvidencePromotionValidator;
import com.portfolio.agent.turn.capability.portfolio.knowledge.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentationComposer;
import com.portfolio.agent.turn.capability.portfolio.presentation.PresentationPolicy;
import com.portfolio.agent.turn.capability.portfolio.retrieval.BundlePortfolioRetrieverAdapter;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioRetrieverPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PostgresPortfolioRetrieverAdapter;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.JdbcPostgresKnowledgeQuery;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSupportEvaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Portfolio capability 的唯一生产装配，Bundle 与 PostgreSQL 都直接实现最终 Port。 */
@Configuration(proxyBeanMethods = false)
public class PortfolioCapabilityConfiguration {

    @Bean
    @Primary
    PortfolioKnowledgeGateway portfolioKnowledgeGateway(
            PublicPortfolioRepository repository) {
        return new LocalPortfolioKnowledgeAdapter(repository);
    }

    @Bean(name = "bundledPortfolioKnowledgeGateway")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioKnowledgeGateway bundledPortfolioKnowledgeGateway(
            @Qualifier("bundledPublicPortfolioRepository") PublicPortfolioRepository repository) {
        return new LocalPortfolioKnowledgeAdapter(repository);
    }

    @Bean("bundlePortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioRetrieverPort bundlePortfolioRetrieverPort(
            PortfolioKnowledgeGateway knowledgeGateway,
            LocalEmbeddingPort embeddingPort,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.retrieval.profile:DISABLED}") String retrievalProfile) {
        return new BundlePortfolioRetrieverAdapter(
                knowledgeGateway, embeddingPort, hybridEnabled(retrievalProfile));
    }

    @Bean("postgresPortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetrieverPort postgresPortfolioRetrieverPort(
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate,
            LocalEmbeddingPort embeddingPort) {
        return new PostgresPortfolioRetrieverAdapter(
                new JdbcPostgresKnowledgeQuery(jdbcTemplate, embeddingPort));
    }

    @Bean("bundlePortfolioRetrieverPort")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioRetrieverPort bundlePortfolioRetrieverPortForPostgres(
            @Qualifier("bundledPortfolioKnowledgeGateway")
            PortfolioKnowledgeGateway knowledgeGateway,
            LocalEmbeddingPort embeddingPort,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.retrieval.profile:DISABLED}") String retrievalProfile) {
        return new BundlePortfolioRetrieverAdapter(
                knowledgeGateway, embeddingPort, hybridEnabled(retrievalProfile));
    }

    @Bean("portfolioEvidenceCapability")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    PortfolioEvidenceCapability bundlePortfolioEvidenceCapability(
            @Qualifier("bundlePortfolioRetrieverPort") PortfolioRetrieverPort bundle) {
        return new PortfolioEvidenceCapability(
                java.util.Map.of(CorpusBackend.BUNDLE, bundle),
                new RetrievalFallbackPolicy(),
                new EvidencePromotionValidator(java.time.Clock.systemUTC()));
    }

    @Bean("portfolioEvidenceCapability")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioEvidenceCapability postgresPortfolioEvidenceCapability(
            @Qualifier("postgresPortfolioRetrieverPort") PortfolioRetrieverPort postgres,
            @Qualifier("bundlePortfolioRetrieverPort") PortfolioRetrieverPort bundle) {
        return new PortfolioEvidenceCapability(
                java.util.Map.of(
                        CorpusBackend.POSTGRESQL, postgres,
                        CorpusBackend.BUNDLE, bundle),
                new RetrievalFallbackPolicy(),
                new EvidencePromotionValidator(java.time.Clock.systemUTC()));
    }

    @Bean
    PortfolioTaskExecutor portfolioTaskExecutor(
            @Qualifier("portfolioEvidenceCapability") PortfolioEvidenceCapability capability,
            @org.springframework.beans.factory.annotation.Value(
                    "${portfolio.database.public.enabled:false}") boolean databaseEnabled) {
        return new PortfolioTaskExecutor(
                new PortfolioInvocationFactory(databaseEnabled
                        ? CorpusBackend.POSTGRESQL
                        : CorpusBackend.BUNDLE),
                capability,
                new PortfolioSemanticResultFactory(new PortfolioSupportEvaluator()),
                new PortfolioPresentationComposer(PresentationPolicy.defaults()));
    }

    private boolean hybridEnabled(String retrievalProfile) {
        return "HYBRID".equalsIgnoreCase(retrievalProfile == null
                ? "" : retrievalProfile.trim());
    }
}
