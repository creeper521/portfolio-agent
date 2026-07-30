package com.portfolio.agent.portfolio.repository.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
public class PublicRuntimeRepositoryConfiguration {

    @Bean(name = "publicPortfolioReadTransactionTemplate")
    TransactionTemplate publicPortfolioReadTransactionTemplate(
            @Qualifier("publicPortfolioTransactionManager") PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    @Bean
    PublicRuntimeSnapshotCodec publicRuntimeSnapshotCodec(ObjectMapper objectMapper) {
        return new PublicRuntimeSnapshotCodec(objectMapper);
    }

    @Bean
    PublicRuntimeSnapshotStore publicRuntimeSnapshotStore(
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new JdbcPublicRuntimeSnapshotStore(jdbcTemplate);
    }

    @Bean
    PublicPortfolioRepository postgresPublicPortfolioRepository(
            PublicRuntimeSnapshotStore store,
            @Qualifier("publicPortfolioReadTransactionTemplate") TransactionTemplate readTransactions,
            PublicRuntimeSnapshotCodec snapshotCodec) {
        return new PostgresPublicPortfolioRepository(store, readTransactions, snapshotCodec);
    }
}
