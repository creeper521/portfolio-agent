package com.portfolio.agent.turn.state.postgres.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;
import com.portfolio.agent.turn.state.postgres.AgentStatePayloadCodec;
import com.portfolio.agent.turn.state.postgres.AgentStateCleanupJob;
import com.portfolio.agent.turn.state.postgres.AgentStateKeyCoverageReadiness;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** One production Agent State DataSource and transaction boundary. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "portfolio.conversation-context", name = "mode", havingValue = "POSTGRESQL")
@EnableConfigurationProperties({
        ConversationContextProperties.class, ConversationContextDatabaseProperties.class,
        com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties.class})
public class ConversationContextDatabaseConfiguration {
    @Bean(name = "conversationContextDataSource", destroyMethod = "close")
    DataSource dataSource(
            ConversationContextDatabaseProperties properties,
            com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties runtimeProperties) {
        return new HikariDataSource(hikariConfig(properties, runtimeProperties));
    }

    HikariConfig hikariConfig(
            ConversationContextDatabaseProperties properties,
            com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties runtimeProperties) {
        properties.validate();
        runtimeProperties.validateBudgetRelation();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-agent-state");
        // 8 个 Active Turn 之外为 cleanup 与会话校验保留两个连接。
        config.setMaximumPoolSize(Math.max(10, runtimeProperties.getMaxActiveTurns() + 2));
        config.setConnectionTimeout(runtimeProperties.getDatabaseOperationTimeout().toMillis());
        long networkSeconds = Math.max(1,
                (runtimeProperties.getDatabaseOperationTimeout().toMillis() + 999) / 1000);
        config.addDataSourceProperty("connectTimeout", Long.toString(networkSeconds));
        config.addDataSourceProperty("socketTimeout", Long.toString(networkSeconds));
        config.addDataSourceProperty("cancelSignalTimeout", Long.toString(networkSeconds));
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.setReadOnly(false);
        return config;
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
        properties.validate();
        byte[] currentPayloadKey = decode(
                properties.getCrypto().getCurrentPayloadKey(), "payload key");
        byte[] currentTokenKey = decode(
                properties.getCrypto().getCurrentTokenKey(), "token key");
        if (java.security.MessageDigest.isEqual(currentTokenKey, currentPayloadKey)) {
            throw new IllegalStateException("token and payload keys must be different");
        }
        Map<String, byte[]> previousKeys = optionalKey(
                properties.getCrypto().getPreviousPayloadKeyId(),
                properties.getCrypto().getPreviousPayloadKey(), "previous payload key");
        return new AgentStatePayloadCodec(
                mapper, required(properties.getCrypto().getCurrentPayloadKeyId(), "payload key id"),
                currentPayloadKey, previousKeys);
    }

    @Bean
    JdbcAgentStateStore jdbcAgentStateStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            AgentStatePayloadCodec codec, ConversationContextProperties properties,
            ConversationContextDatabaseProperties database,
            com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties runtimeProperties) {
        JdbcAgentStateStore store = new JdbcAgentStateStore(
                jdbc, transactions, codec, database.getSchema(), properties.getAbsoluteTtl(),
                properties.getClarificationTtl(),
                required(properties.getCrypto().getCurrentTokenKeyId(), "token key id"),
                supportedIds(
                        properties.getCrypto().getCurrentTokenKeyId(),
                        properties.getCrypto().getPreviousTokenKeyId()),
                required(properties.getCrypto().getCurrentTokenKeyId(), "fingerprint key id"),
                supportedIds(
                        properties.getCrypto().getCurrentTokenKeyId(),
                        properties.getCrypto().getPreviousTokenKeyId()),
                runtimeProperties.getDatabaseOperationTimeout(),
                properties.getCleanupBatchSize(), java.time.Clock.systemUTC());
        return store;
    }

    @Bean
    ConversationSessionStore jdbcConversationSessionStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            ConversationContextProperties properties,
            ConversationContextDatabaseProperties database,
            com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties runtimeProperties,
            AgentStatePayloadCodec codec) {
        return new JdbcConversationSessionStore(
                jdbc, transactions, database.getSchema(),
                required(properties.getCrypto().getCurrentTokenKeyId(), "token key id"),
                supportedIds(
                        properties.getCrypto().getCurrentTokenKeyId(),
                        properties.getCrypto().getPreviousTokenKeyId()),
                runtimeProperties.getDatabaseOperationTimeout(),
                java.time.Clock.systemUTC(), codec);
    }

    @Bean
    AgentStateCleanupJob agentStateCleanupJob(JdbcAgentStateStore store) {
        return new AgentStateCleanupJob(store);
    }

    @Bean
    AgentStateKeyCoverageReadiness agentStateKeyCoverageReadiness(
            JdbcAgentStateStore store) {
        return new AgentStateKeyCoverageReadiness(store, java.time.Clock.systemUTC());
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

    private static Map<String, byte[]> optionalKey(
            String keyId, String encoded, String name) {
        if (keyId == null || keyId.isBlank()) return Map.of();
        return Map.of(required(keyId, name + " id"), decode(encoded, name));
    }

    private static Set<String> supportedIds(String current, String previous) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        ids.add(required(current, "token key id"));
        if (previous != null && !previous.isBlank()) ids.add(previous.trim());
        return Set.copyOf(ids);
    }
}
