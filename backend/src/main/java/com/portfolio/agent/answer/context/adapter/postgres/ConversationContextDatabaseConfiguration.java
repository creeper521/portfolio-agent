package com.portfolio.agent.answer.context.adapter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.state.postgres.AgentStatePayloadCodec;
import com.portfolio.agent.turn.state.postgres.JdbcAgentStateStore;
import com.portfolio.agent.turn.state.postgres.JdbcConversationSessionStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Base64;

/** One production Agent State DataSource and transaction boundary. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "portfolio.conversation-context", name = "mode", havingValue = "POSTGRESQL")
@EnableConfigurationProperties({
        ConversationContextProperties.class, ConversationContextDatabaseProperties.class})
public class ConversationContextDatabaseConfiguration {
    @Bean(name = "conversationContextDataSource", destroyMethod = "close")
    DataSource dataSource(ConversationContextDatabaseProperties properties) {
        properties.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-agent-state");
        config.setMaximumPoolSize(5);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    @Bean(name = "conversationContextFlyway")
    Flyway flyway(
            @Qualifier("conversationContextDataSource") DataSource dataSource,
            ConversationContextDatabaseProperties properties) {
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .schemas(properties.getSchema()).defaultSchema(properties.getSchema())
                .locations("classpath:db/context").table("flyway_schema_history_context").load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "conversationContextJdbcTemplate")
    JdbcTemplate jdbc(
            @Qualifier("conversationContextDataSource") DataSource dataSource,
            @Qualifier("conversationContextFlyway") Flyway flyway) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "conversationContextTransactionManager")
    PlatformTransactionManager transactionManager(
            @Qualifier("conversationContextDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "conversationContextTransactionTemplate")
    TransactionTemplate transactions(
            @Qualifier("conversationContextTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    AgentStatePayloadCodec agentStatePayloadCodec(
            ObjectMapper mapper, ConversationContextProperties properties) {
        return new AgentStatePayloadCodec(
                mapper, required(properties.getCrypto().getCurrentPayloadKeyId(), "payload key id"),
                decode(properties.getCrypto().getCurrentPayloadKey(), "payload key"));
    }

    @Bean
    AgentStateStore jdbcAgentStateStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            AgentStatePayloadCodec codec, ConversationContextProperties properties,
            ConversationContextDatabaseProperties database) {
        return new JdbcAgentStateStore(
                jdbc, transactions, codec, database.getSchema(), Duration.ofMinutes(30),
                required(properties.getCrypto().getCurrentTokenKeyId(), "token key id"));
    }

    @Bean
    ConversationSessionStore jdbcConversationSessionStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            ConversationContextProperties properties,
            ConversationContextDatabaseProperties database) {
        return new JdbcConversationSessionStore(
                jdbc, transactions, database.getSchema(),
                required(properties.getCrypto().getCurrentTokenKeyId(), "token key id"));
    }

    private static byte[] decode(String value, String name) {
        try {
            byte[] decoded = Base64.getDecoder().decode(required(value, name));
            if (decoded.length != 32) throw new IllegalStateException(name + " must be 32 bytes");
            return decoded;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException(name + " must be base64", failure);
        }
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }
}
