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

/**
 * POSTGRESQL 模式的 Agent State 数据源与事务边界装配。
 *
 * <p>提供独立的 Hikari 连接池（容量对齐 Active Turn 预算）、Flyway 迁移、
 * JdbcTemplate 与事务模板，并装配加密 codec、JDBC State/会话存储、定时清理与
 * 密钥覆盖就绪门。连接池与网络超时全部对齐 databaseOperationTimeout，防止单条
 * 语句占用超出 Turn 预算的时间。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "portfolio.conversation-context", name = "mode", havingValue = "POSTGRESQL")
@EnableConfigurationProperties({
        ConversationContextProperties.class, ConversationContextDatabaseProperties.class,
        com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties.class})
public class ConversationContextDatabaseConfiguration {
    /** Agent State 专用 Hikari 连接池（独立于业务只读数据源）。 */
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

    /** State schema 的 Flyway 迁移（独立历史表），在 JdbcTemplate 装配前执行。 */
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

    /** State 专用 JdbcTemplate（依赖 Flyway 先完成迁移）。 */
    @Bean(name = "conversationContextJdbcTemplate")
    JdbcTemplate jdbc(
            @Qualifier("conversationContextDataSource") DataSource dataSource,
            @Qualifier("conversationContextFlyway") Flyway flyway) {
        return new JdbcTemplate(dataSource);
    }

    /** State 专用事务管理器与模板（单一事务边界）。 */
    @Bean(name = "conversationContextTransactionManager")
    PlatformTransactionManager transactionManager(
            @Qualifier("conversationContextDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** 编程式事务模板（State 存储的所有事务都经此执行）。 */
    @Bean(name = "conversationContextTransactionTemplate")
    TransactionTemplate transactions(
            @Qualifier("conversationContextTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    /**
     * 载荷加密 codec 装配：校验 TTL 配置、解码载荷/令牌密钥并强制两族密钥不同
     * （fail-closed：base64 非法或长度非 32 字节即启动失败）。
     */
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

    /** JDBC Agent State 权威存储（令牌与指纹密钥同源、支持轮换窗口）。 */
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

    /** 会话存储（注入 codec 以解密行内语义状态）。 */
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

    /** 定时清理任务。 */
    @Bean
    AgentStateCleanupJob agentStateCleanupJob(JdbcAgentStateStore store) {
        return new AgentStateCleanupJob(store);
    }

    /** 启动期密钥覆盖就绪门（初始化即断言，失败拒绝启动）。 */
    @Bean
    AgentStateKeyCoverageReadiness agentStateKeyCoverageReadiness(
            JdbcAgentStateStore store) {
        return new AgentStateKeyCoverageReadiness(store, java.time.Clock.systemUTC());
    }

    /** 解码 base64 密钥；长度必须恰为 32 字节（AES-256），否则启动失败。 */
    private static byte[] decode(String value, String name) {
        try {
            byte[] decoded = Base64.getDecoder().decode(required(value, name));
            if (decoded.length != 32) throw new IllegalStateException(name + " must be 32 bytes");
            return decoded;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException(name + " must be base64", failure);
        }
    }
    /** 必填配置项读取：缺失即启动失败。 */
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }

    /** 可选的 previous 密钥：keyId 与值必须成对出现，缺省返回空映射。 */
    private static Map<String, byte[]> optionalKey(
            String keyId, String encoded, String name) {
        if (keyId == null || keyId.isBlank()) return Map.of();
        return Map.of(required(keyId, name + " id"), decode(encoded, name));
    }

    /** 当前 + previous 密钥 id 的受支持集合（轮换窗口）。 */
    private static Set<String> supportedIds(String current, String previous) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        ids.add(required(current, "token key id"));
        if (previous != null && !previous.isBlank()) ids.add(previous.trim());
        return Set.copyOf(ids);
    }
}
