package com.portfolio.agent.ingestion.adapter.postgres;

import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import com.portfolio.agent.ingestion.service.MarkdownImportService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(prefix = "portfolio.database.governance", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GovernanceDatabaseProperties.class)
public class GovernanceDatabaseConfiguration {

    @Bean(name = "governanceDataSource", destroyMethod = "close")
    DataSource governanceDataSource(GovernanceDatabaseProperties properties) {
        properties.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-governance");
        config.setMaximumPoolSize(3);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    @Bean(name = "governanceFlyway")
    Flyway governanceFlyway(@Qualifier("governanceDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/governance")
                .table("flyway_schema_history_governance").load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "governanceJdbcTemplate")
    JdbcTemplate governanceJdbcTemplate(
            @Qualifier("governanceDataSource") DataSource dataSource,
            @Qualifier("governanceFlyway") Flyway flyway) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "governanceTransactionManager")
    PlatformTransactionManager governanceTransactionManager(
            @Qualifier("governanceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "governanceTransactionTemplate")
    TransactionTemplate governanceTransactionTemplate(
            @Qualifier("governanceTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean(name = "governanceMarkdownGovernanceStore")
    MarkdownGovernanceStore governanceMarkdownGovernanceStore(
            @Qualifier("governanceJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new PostgresSourceDocumentRepository(jdbcTemplate);
    }

    @Bean(name = "governanceMarkdownImportService")
    MarkdownImportService governanceMarkdownImportService(
            @Qualifier("governanceMarkdownGovernanceStore") MarkdownGovernanceStore store,
            @Qualifier("governanceDocumentEmbeddingPort") DocumentEmbeddingPort embeddingPort,
            @Qualifier("governanceTransactionTemplate") TransactionTemplate transactions) {
        return new MarkdownImportService(store, embeddingPort, transactions);
    }
}
