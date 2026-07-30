package com.portfolio.agent.portfolio.repository.postgres;

import com.portfolio.agent.portfolio.service.PublicReleaseActivationService;
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
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(PublicPortfolioDatabaseProperties.class)
public class PublicPortfolioDatabaseConfiguration {

    @Bean(name = "publicPortfolioDataSource", destroyMethod = "close")
    DataSource publicPortfolioDataSource(PublicPortfolioDatabaseProperties properties) {
        properties.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-public");
        config.setMaximumPoolSize(5);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    @Bean
    Flyway publicPortfolioFlyway(
            @Qualifier("publicPortfolioDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/public")
                .table("flyway_schema_history_public")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "publicPortfolioJdbcTemplate")
    JdbcTemplate publicPortfolioJdbcTemplate(
            @Qualifier("publicPortfolioDataSource") DataSource dataSource,
            Flyway publicPortfolioFlyway) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "publicPortfolioTransactionManager")
    PlatformTransactionManager publicPortfolioTransactionManager(
            @Qualifier("publicPortfolioDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "publicPortfolioTransactionTemplate")
    TransactionTemplate publicPortfolioTransactionTemplate(
            @Qualifier("publicPortfolioTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    PublicBundleDatabaseImporter publicBundleDatabaseImporter(
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("publicPortfolioTransactionTemplate") TransactionTemplate publicPortfolioTransactionTemplate,
            PublicRuntimeSnapshotCodec snapshotCodec) {
        return new PublicBundleDatabaseImporter(jdbcTemplate, publicPortfolioTransactionTemplate, snapshotCodec);
    }

    @Bean
    PublicReleaseActivationService publicReleaseActivationService(
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("publicPortfolioTransactionTemplate") TransactionTemplate publicPortfolioTransactionTemplate) {
        return new PublicReleaseActivationService(jdbcTemplate, publicPortfolioTransactionTemplate);
    }

}
