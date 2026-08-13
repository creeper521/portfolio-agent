package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.crypto.JdkContextEnvelopeCryptographyAdapter;
import com.portfolio.agent.answer.context.crypto.JdkResumeTokenHashAdapter;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRequestReceiptStoreIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private JdbcRequestReceiptStore store;
    private final Instant now = Instant.parse("2026-08-12T00:00:00Z");

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context")
                .schemas("agent_context")
                .defaultSchema("agent_context")
                .table("flyway_schema_history_context")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context")
                .schemas("agent_context")
                .defaultSchema("agent_context")
                .table("flyway_schema_history_context")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        store = new JdbcRequestReceiptStore(jdbc, transactions,
                new JdkContextEnvelopeCryptographyAdapter("k1", bytes((byte) 1), null, null),
                new JdkResumeTokenHashAdapter("h1", bytes((byte) 2), null, null),
                ConversationContextCapacityPolicy.defaults(), "agent_context");
    }

    @Test
    void claimsEncryptsAndReturnsTheSameCompletionReceipt() {
        UUID requestToken = UUID.randomUUID();
        ConversationId conversationId = ConversationId.random();
        ResumeToken resumeToken = ResumeToken.issue();
        RequestFingerprint fingerprint = RequestFingerprint.sha256Canonical("turn|question");

        RequestReceiptStore.ClaimResult claim = store.claim(
                requestToken, conversationId, resumeToken, fingerprint, null, now);
        assertEquals(RequestReceiptStore.ClaimResult.Status.CLAIMED,
                claim.getStatus());
        CompletionReceipt expected = new CompletionReceipt(requestToken, conversationId, fingerprint, null,
                ConversationContinuationStatus.AVAILABLE, now.plusSeconds(2));
        store.complete(requestToken, claim.getLeaseId().orElseThrow(), expected, now.plusSeconds(2));

        assertEquals(expected, store.findCompleted(requestToken, now.plusSeconds(3)).orElseThrow());
        assertNotNull(store.claim(requestToken, conversationId, resumeToken, fingerprint, null, now.plusSeconds(3))
                .getCompletionReceipt().orElseThrow());
    }

    private static byte[] bytes(byte value) {
        byte[] result = new byte[32];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
