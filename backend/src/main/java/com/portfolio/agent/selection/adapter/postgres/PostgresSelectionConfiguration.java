package com.portfolio.agent.selection.adapter.postgres;

import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.selection.gateway.CandidateRetrievalPort;
import com.portfolio.agent.selection.mapper.PortfolioSelectionResponseMapper;
import com.portfolio.agent.selection.service.ExhaustiveSelectionStrategy;
import com.portfolio.agent.selection.service.PortfolioSelectionService;
import com.portfolio.agent.selection.service.SelectionStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
public class PostgresSelectionConfiguration {

    @Bean
    JdbcPostgresSelectionQuery postgresSelectionQuery(
            @Qualifier("publicPortfolioJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new JdbcPostgresSelectionQuery(jdbcTemplate);
    }

    @Bean
    CandidateRetrievalPort candidateRetrievalPort(
            JdbcPostgresSelectionQuery query,
            LocalEmbeddingPort embeddingPort) {
        return new PostgresHybridCandidateRetriever(query, embeddingPort);
    }

    @Bean
    SelectionStrategy selectionStrategy() {
        return new ExhaustiveSelectionStrategy();
    }

    @Bean
    PortfolioSelectionService portfolioSelectionService(
            CandidateRetrievalPort retrievalPort,
            SelectionStrategy strategy) {
        return new PortfolioSelectionService(retrievalPort, strategy);
    }

    @Bean
    PortfolioSelectionResponseMapper portfolioSelectionResponseMapper() {
        return new PortfolioSelectionResponseMapper();
    }
}
