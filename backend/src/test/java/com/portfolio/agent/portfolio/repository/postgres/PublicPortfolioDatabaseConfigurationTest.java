package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
import com.portfolio.agent.portfolio.repository.file.BundledPublicPortfolioRepositoryConfiguration;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.portfolio.controller.PortfolioController;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeReadiness;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class PublicPortfolioDatabaseConfigurationTest {

    @Test
    void reservesThePublicTransactionTemplateBeanNameForATransactionTemplate() throws Exception {
        Method flyway = PublicPortfolioDatabaseConfiguration.class.getDeclaredMethod(
                "publicPortfolioFlyway", DataSource.class);
        Method transactionTemplate = PublicPortfolioDatabaseConfiguration.class.getDeclaredMethod(
                "publicPortfolioTransactionTemplate", PlatformTransactionManager.class);

        assertThat(beanNames(flyway)).doesNotContain("publicPortfolioTransactionTemplate");
        assertThat(beanNames(transactionTemplate)).containsExactly("publicPortfolioTransactionTemplate");
        assertThat(flyway.getReturnType()).isEqualTo(Flyway.class);
        assertThat(transactionTemplate.getReturnType()).isEqualTo(TransactionTemplate.class);
    }

    @Test
    void fileRepositoryIsTheDefaultAndPostgresRepositoryIsExclusiveWhenEnabled() throws Exception {
        ConditionalOnProperty fileCondition =
                JsonPublicPortfolioRepository.class.getAnnotation(ConditionalOnProperty.class);
        ConditionalOnProperty postgresCondition =
                PublicPortfolioDatabaseConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        Method repository = PublicRuntimeRepositoryConfiguration.class.getDeclaredMethod(
                "postgresPublicPortfolioRepository",
                PublicRuntimeSnapshotStore.class,
                TransactionTemplate.class,
                PublicRuntimeSnapshotCodec.class);

        assertThat(fileCondition).isNotNull();
        assertThat(fileCondition.name()).containsExactly("enabled");
        assertThat(fileCondition.havingValue()).isEqualTo("false");
        assertThat(fileCondition.matchIfMissing()).isTrue();
        assertThat(postgresCondition.havingValue()).isEqualTo("true");
        assertThat(repository.getReturnType()).isEqualTo(PublicPortfolioRepository.class);
    }

    @Test
    void postgresRuntimeRepositoryUsesADedicatedReadOnlyTransactionTemplate() throws Exception {
        Method readTransaction = PublicRuntimeRepositoryConfiguration.class.getDeclaredMethod(
                "publicPortfolioReadTransactionTemplate", PlatformTransactionManager.class);

        assertThat(beanNames(readTransaction)).containsExactly("publicPortfolioReadTransactionTemplate");
        assertThat(readTransaction.getReturnType()).isEqualTo(TransactionTemplate.class);
    }

    @Test
    void actualSpringContextUsesOnlyTheJsonRepositoryByDefault() {
        repositoryContext().run(context -> {
            assertThat(context).hasSingleBean(PublicPortfolioRepository.class);
            assertThat(context.getBean(PublicPortfolioRepository.class))
                    .isInstanceOf(JsonPublicPortfolioRepository.class);
            assertThat(context).hasSingleBean(PortfolioService.class);
            assertThat(context).hasSingleBean(PortfolioController.class);
        });
    }

    @Test
    void actualSpringContextUsesOnlyThePostgresRepositoryWhenEnabledWithoutConsumerAmbiguity() {
        repositoryContext()
                .withPropertyValues("portfolio.database.public.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PublicPortfolioRepository.class)).hasSize(2);
                    assertThat(context.getBean(PublicPortfolioRepository.class))
                            .isInstanceOf(PostgresPublicPortfolioRepository.class);
                    assertThat(context.getBean("bundledPublicPortfolioRepository"))
                            .isInstanceOf(JsonPublicPortfolioRepository.class);
                    assertThat(context).hasSingleBean(PortfolioService.class);
                    assertThat(context).hasSingleBean(PortfolioController.class);
                    TransactionTemplate readTransactions =
                            context.getBean("publicPortfolioReadTransactionTemplate", TransactionTemplate.class);
                    assertThat(readTransactions.isReadOnly()).isTrue();
                    PlatformTransactionManager transactionManager =
                            context.getBean("publicPortfolioTransactionManager", PlatformTransactionManager.class);
                    readTransactions.executeWithoutResult(status -> {
                    });
                    ArgumentCaptor<TransactionDefinition> definition =
                            ArgumentCaptor.forClass(TransactionDefinition.class);
                    verify(transactionManager).getTransaction(definition.capture());
                    assertThat(definition.getValue().isReadOnly()).isTrue();
                });
    }

    private ApplicationContextRunner repositoryContext() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        RuntimeRepositoryTestDependencies.class,
                        JsonPublicPortfolioRepository.class,
                        BundledPublicPortfolioRepositoryConfiguration.class,
                        PublicRuntimeRepositoryConfiguration.class,
                        PortfolioService.class,
                        PortfolioController.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeRepositoryTestDependencies {

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
            return mock(ApplicationStartupDiagnostics.class);
        }

        @Bean
        AgentRuntimeReadiness agentRuntimeReadiness() {
            return mock(AgentRuntimeReadiness.class);
        }

        @Bean
        ModelCatalogSnapshot modelCatalogSnapshot() {
            return ModelCatalogSnapshot.empty();
        }

        @Bean
        PortfolioResponseMapper portfolioResponseMapper() {
            return mock(PortfolioResponseMapper.class);
        }

        @Bean(name = "publicPortfolioJdbcTemplate")
        JdbcTemplate publicPortfolioJdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean(name = "publicPortfolioTransactionManager")
        PlatformTransactionManager publicPortfolioTransactionManager() {
            PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
            when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(mock(TransactionStatus.class));
            return transactionManager;
        }
    }

    private String[] beanNames(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        return bean.name();
    }
}
