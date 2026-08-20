package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.execution.TurnDeadline;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class AgentStateCleanupIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context").schemas("agent_context")
                .defaultSchema("agent_context").table("flyway_schema_history_context")
                .cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context").schemas("agent_context")
                .defaultSchema("agent_context").table("flyway_schema_history_context")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void finalMigrationRemovesRetiredV1StateSurface() {
        assertThat(tableExists("conversation_session")).isTrue();
        assertThat(tableExists("agent_turn_execution")).isTrue();
        assertThat(tableExists("agent_turn_context")).isTrue();
        assertThat(tableExists("agent_turn_clarification")).isTrue();
        assertThat(tableExists("conversation_context")).isFalse();
        assertThat(tableExists("conversation_active_context")).isFalse();
        assertThat(tableExists("conversation_request_receipt")).isFalse();
    }

    @Test
    void finalExecutionConstraintsRejectInvalidTerminalAndCiphertextShapes() {
        UUID conversationId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO agent_context.agent_turn_execution "
                        + "(request_id, conversation_id, request_fingerprint, status, lease_expires_at, "
                        + "fingerprint_key_id, created_at, updated_at, absolute_expires_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), conversationId, new byte[32], "CANCELLED",
                NOW.atOffset(ZoneOffset.UTC), "test-current", NOW.atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC), NOW.plusSeconds(30).atOffset(ZoneOffset.UTC)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO agent_context.agent_turn_execution "
                        + "(request_id, conversation_id, request_fingerprint, status, lease_expires_at, "
                        + "fingerprint_key_id, settlement_key_id, settlement_nonce, settlement_ciphertext,"
                        + " created_at, updated_at, terminal_at, absolute_expires_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), conversationId, new byte[32], "COMPLETED",
                NOW.atOffset(ZoneOffset.UTC), "test-current",
                "current-payload", new byte[12], new byte[8],
                NOW.atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC), NOW.plusSeconds(30).atOffset(ZoneOffset.UTC)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void cleanupUsesOneBoundedBudgetAcrossExpiredAndUnsupportedRows() {
        JdbcAgentStateStore store = store(2);
        insertExpiredExecution(UUID.randomUUID(), "current-payload");
        insertExpiredExecution(UUID.randomUUID(), "retired-payload");
        insertExpiredSession(UUID.randomUUID(), "current-token");

        JdbcAgentStateStore.CleanupResult first = store.cleanup(NOW);
        assertThat(first.total()).isEqualTo(2);
        assertThat(rowCount()).isEqualTo(1);

        JdbcAgentStateStore.CleanupResult second = store.cleanup(NOW);
        assertThat(second.total()).isEqualTo(1);
        assertThat(rowCount()).isZero();
    }

    @Test
    void cleanupBatchCountsPhysicalChildRowsBeforeDeletingTheirParent() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        insertCompletedExecution(
                requestId, conversationId, "current-payload", NOW.minusSeconds(1));
        insertContext(requestId, conversationId, "context-child-a");
        insertContext(requestId, conversationId, "context-child-b");
        JdbcAgentStateStore store = store(2);

        JdbcAgentStateStore.CleanupResult first = store.cleanup(NOW);

        assertThat(first.total()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_execution", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_context", Integer.class))
                .isZero();
        assertThat(store.cleanup(NOW).total()).isEqualTo(1);
    }

    @Test
    void unavailableKeyFailsBeforeServingAndCannotDeleteUnexpiredState() {
        UUID requestId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        insertCompletedExecution(requestId, conversationId, "retired-payload", NOW.plusSeconds(900));
        insertSession(conversationId, new byte[32], "retired-token", NOW.plusSeconds(900), null);

        JdbcAgentStateStore cleaner = store(20);
        assertThatThrownBy(() -> cleaner.assertKeyCoverage(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpired Agent State");
        assertThatThrownBy(() -> cleaner.cleanup(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rowCount()).isEqualTo(2);

        JdbcAgentStateStore.CleanupResult result = cleaner.cleanup(NOW.plusSeconds(901));
        assertThat(result.total()).isEqualTo(2);
        assertThat(rowCount()).isZero();
    }

    @Test
    void revokedSessionIsInvisibleThenRemovedByCleanup() {
        UUID conversationId = UUID.randomUUID();
        byte[] tokenHash = new byte[32];
        JdbcConversationSessionStore sessions = new JdbcConversationSessionStore(
                jdbc, transactions, "agent_context", "current-token", Set.of("current-token"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        sessions.save(new ConversationSessionStore.Session(
                conversationId.toString(), tokenHash, NOW, NOW.plus(Duration.ofMinutes(30))));

        sessions.revokeForTest(conversationId.toString());
        sessions.save(new ConversationSessionStore.Session(
                conversationId.toString(), tokenHash, NOW, NOW.plus(Duration.ofMinutes(30))));

        assertThat(findSession(sessions, tokenHash, NOW.plusSeconds(1))).isEmpty();
        JdbcAgentStateStore cleaner = store(20);
        assertThat(cleaner.cleanup(NOW.plus(Duration.ofMinutes(29))).total()).isZero();
        assertThat(rowCount()).isEqualTo(1);
        JdbcAgentStateStore.CleanupResult result =
                cleaner.cleanup(NOW.plus(Duration.ofMinutes(30)));
        assertThat(result.revokedSessions()).isEqualTo(1);
        assertThat(rowCount()).isZero();
    }

    @Test
    void newerTentativeSessionCanReplaceExpiredOrRevokedRowWithoutRevivingOldToken() {
        JdbcConversationSessionStore sessions = new JdbcConversationSessionStore(
                jdbc, transactions, "agent_context", "current-token", Set.of("current-token"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID expiredConversation = UUID.randomUUID();
        byte[] expiredHash = new byte[32];
        byte[] freshHash = new byte[32];
        freshHash[0] = 1;
        sessions.save(new ConversationSessionStore.Session(
                expiredConversation.toString(), expiredHash, NOW,
                NOW.plus(Duration.ofMinutes(30))));
        sessions.save(new ConversationSessionStore.Session(
                expiredConversation.toString(), freshHash,
                NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(60))));
        assertThat(findSession(sessions, expiredHash, NOW.plus(Duration.ofMinutes(31)))).isEmpty();
        assertThat(findSession(sessions, freshHash, NOW.plus(Duration.ofMinutes(59)))).isPresent();

        UUID revokedConversation = UUID.randomUUID();
        byte[] revokedHash = new byte[32];
        byte[] replacementHash = new byte[32];
        replacementHash[0] = 2;
        sessions.save(new ConversationSessionStore.Session(
                revokedConversation.toString(), revokedHash, NOW,
                NOW.plus(Duration.ofMinutes(30))));
        sessions.revokeForTest(revokedConversation.toString());
        sessions.save(new ConversationSessionStore.Session(
                revokedConversation.toString(), replacementHash, NOW.plusSeconds(1),
                NOW.plus(Duration.ofMinutes(30)).plusSeconds(1)));
        assertThat(findSession(sessions, revokedHash, NOW.plusSeconds(2))).isEmpty();
        assertThat(findSession(sessions, replacementHash, NOW.plusSeconds(2))).isEmpty();
        sessions.save(new ConversationSessionStore.Session(
                revokedConversation.toString(), replacementHash, NOW.plus(Duration.ofMinutes(30)),
                NOW.plus(Duration.ofMinutes(60))));
        assertThat(findSession(sessions, replacementHash, NOW.plus(Duration.ofMinutes(31))))
                .isPresent();
    }

    private JdbcAgentStateStore store(int batchSize) {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule())
                        .addModule(new JavaTimeModule()).build(),
                "current-payload", new byte[32], Map.of());
        return new JdbcAgentStateStore(
                jdbc, transactions, codec, "agent_context", Duration.ofMinutes(30),
                Duration.ofMinutes(5), "current-token", Set.of("current-token"),
                "test-current", Set.of("test-current", "v1", "v2"),
                Duration.ofSeconds(3), batchSize, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='agent_context' AND table_name=?)",
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }

    private int rowCount() {
        Integer executions = jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_execution", Integer.class);
        Integer sessions = jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.conversation_session", Integer.class);
        return executions + sessions;
    }

    private void insertExpiredExecution(UUID requestId, String keyId) {
        insertCompletedExecution(requestId, UUID.randomUUID(), keyId, NOW.minusSeconds(1));
    }

    private void insertCompletedExecution(
            UUID requestId, UUID conversationId, String keyId, Instant expiresAt) {
        jdbc.update("INSERT INTO agent_context.agent_turn_execution "
                        + "(request_id, conversation_id, request_fingerprint, status, lease_expires_at, "
                        + "fingerprint_key_id, "
                        + "settlement_key_id, settlement_nonce, settlement_ciphertext, created_at, updated_at, "
                        + "terminal_at, absolute_expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                requestId, conversationId, new byte[32], "COMPLETED", NOW.atOffset(ZoneOffset.UTC),
                "test-current",
                keyId, new byte[12], new byte[16], NOW.minusSeconds(2).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(1).atOffset(ZoneOffset.UTC), NOW.minusSeconds(1).atOffset(ZoneOffset.UTC),
                expiresAt.atOffset(ZoneOffset.UTC));
    }

    private void insertExpiredSession(UUID conversationId, String keyId) {
        insertSession(conversationId, UUID.randomUUID().toString().getBytes(), keyId,
                NOW.minusSeconds(1), null);
    }

    private void insertSession(
            UUID conversationId, byte[] tokenHash, String keyId,
            Instant expiresAt, Instant revokedAt) {
        byte[] normalizedHash = java.util.Arrays.copyOf(tokenHash, 32);
        jdbc.update("INSERT INTO agent_context.conversation_session "
                        + "(conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, "
                        + "idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision, revoked_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,0,?)",
                conversationId, normalizedHash, keyId, NOW.minusSeconds(2).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(2).atOffset(ZoneOffset.UTC), expiresAt.atOffset(ZoneOffset.UTC),
                expiresAt.atOffset(ZoneOffset.UTC), 0, 0,
                revokedAt == null ? null : revokedAt.atOffset(ZoneOffset.UTC));
    }

    private void insertContext(
            UUID requestId, UUID conversationId, String handle) {
        jdbc.update("INSERT INTO agent_context.agent_turn_context "
                        + "(conversation_id, context_handle, source_request_id, expires_at, "
                        + "payload_key_id, payload_nonce, payload_ciphertext) VALUES (?,?,?,?,?,?,?)",
                conversationId, handle, requestId,
                NOW.plus(Duration.ofMinutes(10)).atOffset(ZoneOffset.UTC),
                "current-payload", new byte[12], new byte[16]);
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(Duration.ofSeconds(3), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private java.util.Optional<ConversationSessionStore.Session> findSession(
            JdbcConversationSessionStore sessions, byte[] hash, Instant at) {
        Clock clock = Clock.fixed(at, ZoneOffset.UTC);
        return sessions.find(java.util.List.of(hash), at,
                new TurnDeadline(at.plusSeconds(5), clock));
    }
}
