package com.portfolio.agent.ingestion.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import com.portfolio.agent.infrastructure.retrieval.adapter.RetrievalProperties;
import com.portfolio.agent.infrastructure.retrieval.adapter.RetrievalConfiguration;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import com.portfolio.agent.portfolio.repository.postgres.PublicPortfolioDatabaseConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class GovernanceDatabaseConfigurationTest {

    @Test
    void applicationConfigurationUsesOnlyEmptyGovernanceEnvironmentPlaceholders()
            throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(configuration)
                    .contains("enabled: ${PORTFOLIO_GOVERNANCE_DATABASE_ENABLED:false}")
                    .contains("url: ${PORTFOLIO_GOVERNANCE_DATABASE_URL:}")
                    .contains("username: ${PORTFOLIO_GOVERNANCE_DATABASE_USERNAME:}")
                    .contains("password: ${PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD:}")
                    .doesNotContain("PORTFOLIO_GOVERNANCE_DATABASE_URL:jdbc:")
                    .doesNotContain("PORTFOLIO_GOVERNANCE_DATABASE_USERNAME:portfolio")
                    .doesNotContain("PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD:portfolio");
        }
    }

    @Test
    void isolatesGovernanceInfrastructureBehindGovernanceBeanNames() throws Exception {
        Method flyway = GovernanceDatabaseConfiguration.class.getDeclaredMethod(
                "governanceFlyway", DataSource.class);
        Method transactionTemplate = GovernanceDatabaseConfiguration.class.getDeclaredMethod(
                "governanceTransactionTemplate", PlatformTransactionManager.class);
        Method documentEmbedding = RetrievalConfiguration.class.getDeclaredMethod(
                "governanceDocumentEmbeddingPort", RetrievalProperties.class);

        assertThat(flyway.getReturnType()).isEqualTo(Flyway.class);
        assertThat(transactionTemplate.getReturnType()).isEqualTo(TransactionTemplate.class);
        assertThat(documentEmbedding.getReturnType()).isEqualTo(DocumentEmbeddingPort.class);
        assertThat(documentEmbedding.getParameterTypes()).containsExactly(RetrievalProperties.class);
        for (Method method : GovernanceDatabaseConfiguration.class.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                assertThat(bean.name()).allSatisfy(name -> assertThat(name).startsWith("governance"));
            }
        }
    }

    @Test
    void keepsPublicAndGovernanceInfrastructureBeanNamesDisjoint() {
        java.util.Set<String> publicNames = beanNames(PublicPortfolioDatabaseConfiguration.class);
        java.util.Set<String> governanceNames = beanNames(GovernanceDatabaseConfiguration.class);

        assertThat(publicNames).doesNotContainAnyElementsOf(governanceNames);
        assertThat(publicNames).allMatch(name -> name.startsWith("publicPortfolio"));
        assertThat(governanceNames).allMatch(name -> name.startsWith("governance"));
    }

    private java.util.Set<String> beanNames(Class<?> type) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                java.util.Collections.addAll(names, bean.name());
            }
        }
        return names;
    }
}
