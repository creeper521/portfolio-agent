package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.crypto.ContextEnvelopeCryptographyPort;
import com.portfolio.agent.answer.context.crypto.JdkContextEnvelopeCryptographyAdapter;
import com.portfolio.agent.answer.context.crypto.JdkResumeTokenHashAdapter;
import com.portfolio.agent.answer.context.crypto.ResumeTokenHashPort;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import com.portfolio.agent.answer.context.service.ConversationContextCommitter;
import com.portfolio.agent.answer.context.service.ConversationContextFacade;
import com.portfolio.agent.answer.context.service.ConversationContextMutationFactory;
import com.portfolio.agent.answer.context.service.ConversationContextResolver;
import com.portfolio.agent.answer.context.service.SafeContextSummaryProjector;
import com.portfolio.agent.answer.context.service.AuthorizedContextReferenceService;
import com.portfolio.agent.answer.context.service.RequestReceiptService;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
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
import java.util.Base64;

/** Independent PostgreSQL wiring; no public/governance DataSource or memory fallback is reused. */
@Configuration
@ConditionalOnProperty(
        prefix = "portfolio.conversation-context", name = "mode", havingValue = "POSTGRESQL")
@EnableConfigurationProperties({ConversationContextProperties.class, ConversationContextDatabaseProperties.class})
public class ConversationContextDatabaseConfiguration {

    @Bean(name = "conversationContextDataSource", destroyMethod = "close")
    DataSource conversationContextDataSource(ConversationContextDatabaseProperties properties) {
        properties.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("portfolio-context");
        config.setMaximumPoolSize(5);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    @Bean(name = "conversationContextFlyway")
    Flyway conversationContextFlyway(
            @Qualifier("conversationContextDataSource") DataSource dataSource,
            ConversationContextDatabaseProperties properties) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(properties.getSchema())
                .defaultSchema(properties.getSchema())
                .locations("classpath:db/context")
                .table("flyway_schema_history_context")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "conversationContextJdbcTemplate")
    JdbcTemplate conversationContextJdbcTemplate(
            @Qualifier("conversationContextDataSource") DataSource dataSource,
            @Qualifier("conversationContextFlyway") Flyway flyway) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "conversationContextTransactionManager")
    PlatformTransactionManager conversationContextTransactionManager(
            @Qualifier("conversationContextDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "conversationContextTransactionTemplate")
    TransactionTemplate conversationContextTransactionTemplate(
            @Qualifier("conversationContextTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    ConversationContextCodecRegistry conversationContextCodecRegistry() {
        return ConversationContextCodecRegistry.defaults();
    }

    @Bean
    ResumeTokenHashPort conversationContextResumeTokenHash(ConversationContextProperties properties) {
        ConversationContextProperties.Crypto crypto = properties.getCrypto();
        return new JdkResumeTokenHashAdapter(
                crypto.getCurrentTokenKeyId(), decode(crypto.getCurrentTokenKey(), "current token key"),
                optionalText(crypto.getPreviousTokenKeyId()),
                decodeOptional(optionalText(crypto.getPreviousTokenKey()), "previous token key"));
    }

    @Bean
    ContextEnvelopeCryptographyPort conversationContextEnvelopeCryptography(
            ConversationContextProperties properties) {
        ConversationContextProperties.Crypto crypto = properties.getCrypto();
        return new JdkContextEnvelopeCryptographyAdapter(
                crypto.getCurrentPayloadKeyId(), decode(crypto.getCurrentPayloadKey(), "current payload key"),
                optionalText(crypto.getPreviousPayloadKeyId()),
                decodeOptional(optionalText(crypto.getPreviousPayloadKey()), "previous payload key"));
    }

    @Bean
    ConversationContextCapacityPolicy conversationContextCapacityPolicy(
            ConversationContextProperties properties) {
        properties.validate();
        return new ConversationContextCapacityPolicy(
                32, 16 * 1024, properties.getIdleTtl(), properties.getAbsoluteTtl());
    }

    @Bean
    ConversationBusinessContextStore conversationBusinessContextStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            ConversationContextCodecRegistry codecRegistry,
            ContextEnvelopeCryptographyPort cryptography,
            ResumeTokenHashPort tokenHash,
            ConversationContextCapacityPolicy capacityPolicy,
            ConversationContextDatabaseProperties properties) {
        return new JdbcConversationBusinessContextStore(
                jdbcTemplate, transactions, codecRegistry, cryptography, tokenHash, capacityPolicy,
                properties.getSchema());
    }

    @Bean
    RequestReceiptStore conversationRequestReceiptStore(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            ContextEnvelopeCryptographyPort cryptography,
            ResumeTokenHashPort tokenHash,
            ConversationContextCapacityPolicy capacityPolicy,
            ConversationContextDatabaseProperties properties) {
        return new JdbcRequestReceiptStore(
                jdbcTemplate, transactions, cryptography, tokenHash, capacityPolicy, properties.getSchema());
    }

    @Bean
    RequestReceiptService requestReceiptService(RequestReceiptStore store) {
        return new RequestReceiptService(store);
    }

    @Bean
    ConversationContextResolver conversationContextResolver(
            ConversationBusinessContextStore store) {
        return new ConversationContextResolver(store);
    }

    @Bean
    ConversationContextFacade conversationContextFacade(
            ConversationBusinessContextStore store,
            ConversationContextResolver resolver) {
        return new ConversationContextFacade(store, resolver, new SafeContextSummaryProjector());
    }

    @Bean
    ConversationContextMutationFactory conversationContextMutationFactory(
            ConversationContextCodecRegistry codecRegistry,
            ConversationContextCapacityPolicy capacityPolicy) {
        return new ConversationContextMutationFactory(codecRegistry, capacityPolicy);
    }

    @Bean
    ConversationContextCommitter conversationContextCommitter(
            ConversationContextFacade facade,
            ConversationContextMutationFactory mutationFactory) {
        return new ConversationContextCommitter(facade, mutationFactory);
    }

    @Bean
    AuthorizedContextReferenceService authorizedContextReferenceService(
            ConversationContextResolver resolver) {
        return new AuthorizedContextReferenceService(resolver);
    }

    @Bean
    ConversationContextCleanupService conversationContextCleanupService(
            @Qualifier("conversationContextJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("conversationContextTransactionTemplate") TransactionTemplate transactions,
            ConversationContextProperties properties,
            ConversationContextDatabaseProperties databaseProperties) {
        return new ConversationContextCleanupService(
                jdbcTemplate, transactions, properties, databaseProperties);
    }

    @Bean
    ConversationContextCleanupJob conversationContextCleanupJob(
            ConversationContextCleanupService cleanupService) {
        return new ConversationContextCleanupJob(cleanupService);
    }

    private static byte[] decode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.trim());
            if (decoded.length < 32) throw new IllegalStateException(name + " must be at least 32 bytes");
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be base64", exception);
        }
    }

    private static byte[] decodeOptional(String value, String name) {
        return value == null || value.isBlank() ? null : decode(value, name);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
