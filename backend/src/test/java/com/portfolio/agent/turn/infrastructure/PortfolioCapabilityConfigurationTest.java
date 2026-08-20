package com.portfolio.agent.turn.infrastructure;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.knowledge.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PortfolioCapabilityConfigurationTest {

    @Test
    void defaultModeUsesTheOnlyJsonRepositoryAndAnUnversionedCapabilityBean() {
        PublicPortfolioRepository json = mock(PublicPortfolioRepository.class);
        new ApplicationContextRunner()
                .withBean("jsonPublicPortfolioRepository", PublicPortfolioRepository.class, () -> json)
                .withBean(LocalEmbeddingPort.class, () -> text -> {
                    throw new IllegalStateException("embedding disabled");
                })
                .withUserConfiguration(PortfolioCapabilityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PortfolioKnowledgeGateway gateway = context.getBean(
                            "portfolioKnowledgeGateway", PortfolioKnowledgeGateway.class);
                    assertThat(gateway).isInstanceOf(LocalPortfolioKnowledgeAdapter.class);
                    assertThat(ReflectionTestUtils.getField(gateway, "repository")).isSameAs(json);
                    assertThat(context).hasBean("portfolioEvidenceCapability");
                    assertThat(context).doesNotHaveBean("portfolioEvidenceCapabilityV2");
                    assertThat(context).hasSingleBean(PortfolioEvidenceCapability.class);
                });
    }

    @Test
    void postgresModeUsesPrimaryRepositoryAndKeepsBundledRepositoryOnlyForFallback() {
        PublicPortfolioRepository postgres = mock(PublicPortfolioRepository.class);
        PublicPortfolioRepository bundled = mock(PublicPortfolioRepository.class);
        new ApplicationContextRunner()
                .withPropertyValues("portfolio.database.public.enabled=true")
                .withBean(
                        "postgresPublicPortfolioRepository",
                        PublicPortfolioRepository.class,
                        () -> postgres,
                        definition -> definition.setPrimary(true))
                .withBean(
                        "bundledPublicPortfolioRepository",
                        PublicPortfolioRepository.class,
                        () -> bundled)
                .withBean(
                        "publicPortfolioJdbcTemplate",
                        JdbcTemplate.class,
                        () -> mock(JdbcTemplate.class))
                .withBean(LocalEmbeddingPort.class, () -> text -> {
                    throw new IllegalStateException("embedding disabled");
                })
                .withUserConfiguration(PortfolioCapabilityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PortfolioKnowledgeGateway primary = context.getBean(
                            "portfolioKnowledgeGateway", PortfolioKnowledgeGateway.class);
                    assertThat(context.getBean(PortfolioKnowledgeGateway.class)).isSameAs(primary);
                    PortfolioKnowledgeGateway fallback = context.getBean(
                            "bundledPortfolioKnowledgeGateway", PortfolioKnowledgeGateway.class);
                    assertThat(ReflectionTestUtils.getField(primary, "repository")).isSameAs(postgres);
                    assertThat(ReflectionTestUtils.getField(fallback, "repository")).isSameAs(bundled);
                    assertThat(context).hasBean("portfolioEvidenceCapability");
                    assertThat(context).doesNotHaveBean("portfolioEvidenceCapabilityV2");
                    assertThat(context).hasSingleBean(PortfolioEvidenceCapability.class);
                });
    }
}
