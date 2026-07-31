package com.portfolio.agent.answer.intelligence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.adapter.bundle.BundlePortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {

        @Bean
        PortfolioKnowledgeGateway portfolioKnowledgeGateway() {
            return () -> null;
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

        @Bean(name = "publicPortfolioJdbcTemplate")
        JdbcTemplate publicPortfolioJdbcTemplate() {
            return new JdbcTemplate(new DriverManagerDataSource(
                    "jdbc:postgresql://localhost:5432/portfolio", "portfolio", "password"));
        }
    }
}
