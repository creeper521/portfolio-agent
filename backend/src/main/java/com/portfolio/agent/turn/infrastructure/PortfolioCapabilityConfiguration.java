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

    /** 主知识网关：公开数据库投影可用时读库，否则读打包快照。 */
    @Bean
    @Primary
    PortfolioKnowledgeGateway portfolioKnowledgeGateway(
            PublicPortfolioRepository repository) {
        return new LocalPortfolioKnowledgeAdapter(repository);
    }

    /** 数据库模式下的打包快照知识网关（供 Bundle 检索回退使用）。 */
    @Bean(name = "bundledPortfolioKnowledgeGateway")
    @ConditionalOnProperty(
            prefix = "portfolio.database.public",
            name = "enabled",
            havingValue = "true")
    PortfolioKnowledgeGateway bundledPortfolioKnowledgeGateway(
            @Qualifier("bundledPublicPortfolioRepository") PublicPortfolioRepository repository) {
        return new LocalPortfolioKnowledgeAdapter(repository);
    }

    /** 快照模式（默认）的检索 Port：基于打包快照 + 可选本地向量混合检索。 */
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

    /** 数据库模式的 PostgreSQL 检索 Port：公开投影 + pgvector 查询。 */
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

    /** 数据库模式下仍注册的 Bundle 检索 Port（同一 bean 名，作为回退语料）。 */
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

    /** 快照模式的 Evidence 能力：仅 Bundle 后端 + 回退策略 + 晋升校验器。 */
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

    /** 数据库模式的 Evidence 能力：PostgreSQL 主后端 + Bundle 回退后端。 */
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

    /** Portfolio 任务执行器：按数据库开关选择默认语料后端并组装执行链。 */
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
