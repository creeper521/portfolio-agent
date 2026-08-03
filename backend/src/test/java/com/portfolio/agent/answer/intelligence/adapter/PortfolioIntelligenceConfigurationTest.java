package com.portfolio.agent.answer.intelligence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.adapter.bundle.BundlePortfolioRetriever;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;
import com.portfolio.agent.answer.intelligence.service.DefaultPortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioRecommendationPolicy;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskResolver;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskValidator;
import com.portfolio.agent.answer.intelligence.service.RecommendationContextValidator;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.PortfolioIntelligenceAnswerAssembler;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

class PortfolioIntelligenceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PortfolioIntelligenceConfiguration.class, Dependencies.class);

    @Test
    void exposesOnlyTheBundleRetrieverWhenThePublicDatabaseIsDisabled() {
        contextRunner
                .withPropertyValues("portfolio.database.public.enabled=false")
                .run(context -> {
                    Map<String, PortfolioRetriever> retrievers =
                            context.getBeansOfType(PortfolioRetriever.class);

                    assertThat(retrievers).hasSize(1);
                    assertThat(retrievers.values()).singleElement()
                            .isInstanceOf(BundlePortfolioRetriever.class);
                    assertIntelligenceBeans(context);
                });
    }

    @Test
    void exposesOnlyTheFailoverRetrieverWhenThePublicDatabaseIsEnabled() {
        contextRunner
                .withPropertyValues("portfolio.database.public.enabled=true")
                .run(context -> {
                    Map<String, PortfolioRetriever> retrievers =
                            context.getBeansOfType(PortfolioRetriever.class);

                    assertThat(retrievers).hasSize(1);
                    assertThat(retrievers.values()).singleElement()
                            .isInstanceOf(FailoverPortfolioRetriever.class);
                    assertIntelligenceBeans(context);
                });
    }

    @Test
    void fallsBackToThePackagedBundleWhenTheDatabaseIsUnavailableOnColdStart() {
        contextRunner
                .withPropertyValues("portfolio.database.public.enabled=true")
                .run(context -> {
                    PortfolioRetriever retriever = context.getBean(PortfolioRetriever.class);

                    PortfolioRetrievalResult result = retriever.retrieve(new PortfolioRetrievalRequest(
                            "PostgreSQL",
                            PortfolioTaskMode.FACT_LOOKUP,
                            PortfolioConditions.empty()));

                    assertThat(result.getSource().getAdapterId()).isEqualTo("BUNDLE");
                    assertThat(result.getContentVersion()).isEqualTo("2026-07-29.1");
                    assertThat(result.isDegraded()).isTrue();
                    assertThat(result.getNoticeCode())
                            .isEqualTo(FailoverPortfolioRetriever.POSTGRES_RETRIEVAL_UNAVAILABLE);
                    assertThat(context.getBean(AtomicInteger.class)).hasValue(0);
                    assertThat(context).hasSingleBean(PortfolioRetriever.class);
                });
    }

    private void assertIntelligenceBeans(
            org.springframework.context.ApplicationContext context) {
        assertThat(context.getBeansOfType(PortfolioTaskResolver.class)).hasSize(1);
        assertThat(context.getBeansOfType(PortfolioTaskValidator.class)).hasSize(1);
        assertThat(context.getBeansOfType(PortfolioRecommendationPolicy.class)).hasSize(1);
        assertThat(context.getBeansOfType(RecommendationContextValidator.class)).hasSize(1);
        assertThat(context.getBeansOfType(PortfolioIntelligence.class)).hasSize(1);
        assertThat(context.getBean(PortfolioIntelligence.class))
                .isInstanceOf(DefaultPortfolioIntelligence.class);
        assertThat(context.getBeansOfType(PortfolioIntelligenceAnswerAssembler.class)).hasSize(1);
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {

        @Bean
        AtomicInteger databaseGatewayCalls() {
            return new AtomicInteger();
        }

        @Bean
        PublicPortfolioRepository publicPortfolioRepository(AtomicInteger databaseGatewayCalls) {
            return () -> {
                databaseGatewayCalls.incrementAndGet();
                throw new IllegalStateException("database-backed gateway unavailable");
            };
        }

        @Bean
        PortfolioKnowledgeGateway portfolioKnowledgeGateway(PublicPortfolioRepository repository) {
            return new LocalPortfolioKnowledgeAdapter(repository);
        }

        @Bean
        LocalEmbeddingPort localEmbeddingPort() {
            return text -> new EmbeddingVector(new float[]{1.0f, 0.0f});
        }

        @Bean
        LocalRetrievalCoordinator localRetrievalCoordinator(LocalEmbeddingPort localEmbeddingPort) {
            return new LocalRetrievalCoordinator(
                    new RetrievalQueryNormalizer(),
                    new KeywordRetriever(),
                    new VectorRetriever(),
                    new ReciprocalRankFusion(),
                    new RetrievalContextValidator(),
                    localEmbeddingPort);
        }

        @Bean
        RetrievalPolicy retrievalPolicy() {
            return RetrievalPolicy.currentRelease();
        }

        @Bean
        PortfolioTaskClassifierPort portfolioTaskClassifierPort() {
            return mock(PortfolioTaskClassifierPort.class);
        }

        @Bean
        ConversationalAgentProperties conversationalAgentProperties() {
            return new ConversationalAgentProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        PortfolioSnapshotValidator portfolioSnapshotValidator() {
            return new PortfolioSnapshotValidator();
        }

        @Bean
        ApplicationStartupDiagnostics applicationStartupDiagnostics() {
            return new ApplicationStartupDiagnostics(
                    event -> { }, false, false, "DISABLED", 12000, 10, 2);
        }

        @Bean(name = "publicPortfolioJdbcTemplate")
        JdbcTemplate publicPortfolioJdbcTemplate() {
            return new JdbcTemplate(new FailingDataSource());
        }
    }

    private static final class FailingDataSource extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("database unavailable");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("database unavailable");
        }
    }
}
