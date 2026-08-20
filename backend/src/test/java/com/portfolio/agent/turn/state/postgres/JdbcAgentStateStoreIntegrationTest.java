package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintSet;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAgentStateStoreIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");
    private JdbcAgentStateStore store;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private final Instant now = Instant.parse("2026-08-18T00:00:00Z");
    private final java.util.Map<UUID, com.portfolio.agent.turn.continuation.ConversationSessionStore.Session>
            testSessions = new java.util.HashMap<>();

    @BeforeEach void setUp() {
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
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcAgentStateStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new AgentStatePayloadCodec(
                        JsonMapper.builder().addModule(new ParameterNamesModule())
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .serializationInclusion(
                                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                                .build(),
                        "state-key-1", new byte[32]),
                "agent_context", Duration.ofMinutes(30), Duration.ofMinutes(5),
                "token-key-1", Set.of("token-key-1"),
                "test-current", Set.of("test-current", "test-previous-0", "v1", "v2"),
                Duration.ofSeconds(3), 500,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void claimAndSettlementUseTheDatabaseCapWhileTheTurnStillHasTime() throws Exception {
        JdbcAgentStateStore capped = new JdbcAgentStateStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new AgentStatePayloadCodec(
                        JsonMapper.builder().addModule(new ParameterNamesModule())
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                        "state-key-1", new byte[32]),
                "agent_context", Duration.ofMinutes(30), "token-key-1",
                Duration.ofMillis(200));
        UUID claimRequest = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        claim(capped, claimRequest, conversationId, fingerprint, now,
                Duration.ofSeconds(10), deadline(Duration.ofSeconds(5)));

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lock(lockConnection, claimRequest);
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> claim(capped,
                    claimRequest, conversationId, fingerprint, now.plusSeconds(11),
                    Duration.ofSeconds(10), deadline(Duration.ofSeconds(5))))
                    .isInstanceOf(DataAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
            lockConnection.rollback();
        }

        UUID contextRequest = UUID.randomUUID();
        String contextHandle = "context_database_lock_1";
        claim(capped, contextRequest, conversationId, fingerprint, now,
                Duration.ofSeconds(10), deadline(Duration.ofSeconds(5)));
        com.portfolio.agent.turn.continuation.ProjectDiscussionContext context =
                new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                contextHandle, conversationId, "public-1", now.plus(Duration.ofMinutes(30)),
                "project-a", java.util.Set.of("project-a"), now, null);
        assertThat(complete(capped,
                contextRequest, fingerprint,
                new PublicAgentTurn.Conversational(contextRequest, "已完成", List.of()),
                List.of(context), List.of(), null, now.plusSeconds(1),
                deadline(Duration.ofSeconds(5)))).isTrue();
        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lockContext(lockConnection, contextHandle);
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> capped.findContext(
                    conversationId, contextHandle, now.plusSeconds(2),
                    deadline(Duration.ofSeconds(5))))
                    .isInstanceOf(DataAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
            lockConnection.rollback();
        }

        UUID clarificationRequest = UUID.randomUUID();
        byte[] tokenHash = new byte[32];
        String clarificationId = "clarification_database_lock_1";
        claim(capped, clarificationRequest, conversationId, fingerprint, now,
                Duration.ofSeconds(10), deadline(Duration.ofSeconds(5)));
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_detail", "推荐数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record challengeRecord = new ClarificationStore.Record(
                conversationId, tokenHash, "public-1", challenge,
                java.util.Map.of("field_detail", java.util.Map.of(
                        "choice_size_2", "size:2")), java.util.Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, java.util.Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));
        assertThat(complete(capped,
                clarificationRequest, fingerprint,
                new PublicAgentTurn.Clarification(
                        clarificationRequest, "需要补充", challenge, List.of()),
                List.of(), List.of(challengeRecord), null, now.plusSeconds(1),
                deadline(Duration.ofSeconds(5)))).isTrue();
        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lockClarification(lockConnection, clarificationId);
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> capped.consumeClarification(
                    clarificationId, conversationId, tokenHash, "public-1",
                    new ClarificationStore.ClarificationAnswer.Choice("choice_size_2"),
                    now.plusSeconds(2), deadline(Duration.ofSeconds(5))))
                    .isInstanceOf(DataAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
            lockConnection.rollback();
        }

        UUID settlementRequest = UUID.randomUUID();
        claim(capped, settlementRequest, conversationId, fingerprint, now,
                Duration.ofSeconds(10), deadline(Duration.ofSeconds(5)));
        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lock(lockConnection, settlementRequest);
            PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                    settlementRequest, "不会越过数据库预算", List.of());
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> complete(capped,
                    settlementRequest, fingerprint, snapshot,
                    List.of(), List.of(), null, now.plusSeconds(1),
                    deadline(Duration.ofSeconds(5))))
                    .isInstanceOf(DataAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
            lockConnection.rollback();
        }
    }

    @Test
    void explicitFindCancelClearAndSessionLookupUseTheStandaloneDatabaseCap() throws Exception {
        JdbcAgentStateStore capped = new JdbcAgentStateStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new AgentStatePayloadCodec(
                        JsonMapper.builder().addModule(new ParameterNamesModule())
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                        "state-key-1", new byte[32]),
                "agent_context", Duration.ofMinutes(30), Duration.ofMinutes(5),
                "token-key-1", Set.of("token-key-1"),
                "test-current", Set.of("test-current", "test-previous-0"),
                Duration.ofMillis(200), 50,
                Clock.fixed(now, ZoneOffset.UTC));
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        JdbcConversationSessionStore sessions = new JdbcConversationSessionStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                "agent_context", "token-key-1", Set.of("token-key-1"),
                Duration.ofMillis(200), Clock.fixed(now, ZoneOffset.UTC));
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30))));
        claim(capped, requestId, conversationId, new byte[32], now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lock(lockConnection, requestId);
            assertBounded(() -> capped.cancel(requestId, conversationId, now.plusSeconds(1)));
            assertBounded(() -> capped.clearConversation(
                    conversationId, tokenHash, now.plusSeconds(1)));
            lockConnection.rollback();
        }
        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lockTable(lockConnection, "agent_turn_execution");
            assertBounded(() -> capped.find(requestId));
            lockConnection.rollback();
        }

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lockTable(lockConnection, "conversation_session");
            assertBounded(() -> sessions.find(
                    List.of(tokenHash), now.plusSeconds(1), deadline(Duration.ofSeconds(5))));
            lockConnection.rollback();
        }
    }

    private void assertBounded(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        long startedAt = System.nanoTime();
        assertThatThrownBy(operation).isInstanceOf(DataAccessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
    }

    private void lock(Connection connection, UUID requestId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT request_id FROM agent_context.agent_turn_execution "
                        + "WHERE request_id=? FOR UPDATE")) {
            statement.setObject(1, requestId);
            statement.executeQuery();
        }
    }

    private void lockClarification(Connection connection, String clarificationId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT clarification_id FROM agent_context.agent_turn_clarification "
                        + "WHERE clarification_id=? FOR UPDATE")) {
            statement.setString(1, clarificationId);
            statement.executeQuery();
        }
    }

    private void lockContext(Connection connection, String contextHandle) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "LOCK TABLE agent_context.agent_turn_context IN ACCESS EXCLUSIVE MODE")) {
            statement.execute();
        }
    }

    private void lockTable(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "LOCK TABLE agent_context." + table + " IN ACCESS EXCLUSIVE MODE")) {
            statement.execute();
        }
    }

    @Test void claimCompleteAndReplayUseOneEncryptedTerminalRow() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        assertThat(claim(store,
                requestId, conversationId, fingerprint, now, Duration.ofSeconds(10),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                requestId, "不可明文保存的最终公开文本", List.of());
        assertThat(complete(store,
                requestId, fingerprint, snapshot, List.of(), List.of(), null,
                now.plusSeconds(1), TurnDeadline.after(
                        Duration.ofSeconds(5), Clock.fixed(now, ZoneOffset.UTC)))).isTrue();
        TurnExecutionStore.ClaimResult replay = claim(store,
                requestId, conversationId, fingerprint, now.plusSeconds(2),
                Duration.ofSeconds(10), deadline(Duration.ofSeconds(5)));
        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(((PublicAgentTurn.Conversational) replay.replay()).getMessage())
                .isEqualTo("不可明文保存的最终公开文本");
        byte[] ciphertext = jdbc.queryForObject(
                "SELECT settlement_ciphertext FROM agent_context.agent_turn_execution WHERE request_id=?",
                byte[].class, requestId);
        assertThat(new String(ciphertext, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("不可明文保存的最终公开文本");
    }

    @Test void completedRequestReplaysAcrossOneFingerprintKeyRotation() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] previousFingerprint = new byte[32];
        java.util.Arrays.fill(previousFingerprint, (byte) 1);
        byte[] currentFingerprint = new byte[32];
        java.util.Arrays.fill(currentFingerprint, (byte) 2);
        claim(store, requestId, conversationId, previousFingerprint, now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                requestId, "轮换后仍重放", List.of());
        assertThat(complete(store,
                requestId, previousFingerprint, snapshot, List.of(), List.of(), null,
                now.plusSeconds(1), deadline(Duration.ofSeconds(5)))).isTrue();

        TurnExecutionStore.ClaimResult replay = claim(store,
                requestId, conversationId,
                new RequestFingerprintSet(currentFingerprint, List.of(previousFingerprint)),
                testAccess(requestId, conversationId, now.plusSeconds(2)), now.plusSeconds(2),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));

        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(replay.replay()).isInstanceOf(PublicAgentTurn.Conversational.class);
        assertThat(((PublicAgentTurn.Conversational) replay.replay()).getMessage())
                .isEqualTo("轮换后仍重放");
        TurnExecutionStore.ClaimResult currentOnlyReplay = claim(store,
                requestId, conversationId, RequestFingerprintSet.single(currentFingerprint),
                testAccess(requestId, conversationId, now.plusSeconds(3)), now.plusSeconds(3),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        assertThat(currentOnlyReplay.status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        store.assertKeyCoverage(now.plusSeconds(3));
    }

    @Test void readinessTracksEveryUnexpiredExecutionFingerprintKeyUntilMigrated() {
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess tentative =
                TurnExecutionStore.SessionAccess.tentative(session);
        TurnExecutionStore.SessionAccess authenticated =
                TurnExecutionStore.SessionAccess.authenticated(conversationId, tokenHash);
        JdbcAgentStateStore v1 = storeWithFingerprintKeys("v1", Set.of("v1"));
        UUID requestA = UUID.randomUUID();
        UUID requestB = UUID.randomUUID();
        byte[] fingerprintA1 = filled((byte) 11);
        byte[] fingerprintB1 = filled((byte) 12);
        RequestFingerprintSet aV1 = new RequestFingerprintSet(
                "v1", fingerprintA1, List.of());
        RequestFingerprintSet bV1 = new RequestFingerprintSet(
                "v1", fingerprintB1, List.of());
        v1.claim(requestA, conversationId, aV1, tentative, now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        v1.complete(requestA, fingerprintA1,
                new PublicAgentTurn.Conversational(requestA, "A", List.of()),
                List.of(), List.of(), session, tentative, now.plusSeconds(1),
                deadline(Duration.ofSeconds(5)));
        v1.claim(requestB, conversationId, bV1, authenticated, now.plusSeconds(2),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        v1.complete(requestB, fingerprintB1,
                new PublicAgentTurn.Conversational(requestB, "B", List.of()),
                List.of(), List.of(), null, authenticated, now.plusSeconds(3),
                deadline(Duration.ofSeconds(5)));

        byte[] fingerprintA2 = filled((byte) 21);
        byte[] fingerprintB2 = filled((byte) 22);
        JdbcAgentStateStore v2WithPrevious =
                storeWithFingerprintKeys("v2", Set.of("v1", "v2"));
        assertThat(v2WithPrevious.claim(
                requestA, conversationId,
                new RequestFingerprintSet("v2", fingerprintA2, List.of(
                        new RequestFingerprintSet.Candidate("v1", fingerprintA1))),
                authenticated, now.plusSeconds(4), Duration.ofSeconds(35),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);

        JdbcAgentStateStore v2Only = storeWithFingerprintKeys("v2", Set.of("v2"));
        assertThatThrownBy(() -> v2Only.assertKeyCoverage(now.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(v2WithPrevious.claim(
                requestB, conversationId,
                new RequestFingerprintSet("v2", fingerprintB2, List.of(
                        new RequestFingerprintSet.Candidate("v1", fingerprintB1))),
                authenticated, now.plusSeconds(6), Duration.ofSeconds(35),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        v2Only.assertKeyCoverage(now.plusSeconds(7));
        assertThat(v2Only.claim(
                requestA, conversationId,
                new RequestFingerprintSet("v2", fingerprintA2, List.of()),
                authenticated, now.plusSeconds(7), Duration.ofSeconds(35),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(v2Only.claim(
                requestB, conversationId,
                new RequestFingerprintSet("v2", fingerprintB2, List.of()),
                authenticated, now.plusSeconds(7), Duration.ofSeconds(35),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
    }

    @Test void configuredCurrentFingerprintKeyMismatchFailsBeforeWritingState() {
        JdbcAgentStateStore configured =
                storeWithFingerprintKeys("v2", Set.of("v1", "v2"));
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, new byte[32], now, now.plus(Duration.ofMinutes(30)));

        assertThatThrownBy(() -> configured.claim(
                requestId, conversationId,
                new RequestFingerprintSet("v1", new byte[32], List.of()),
                TurnExecutionStore.SessionAccess.tentative(session), now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current key id does not match");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_execution WHERE request_id=?",
                Integer.class, requestId)).isZero();
    }

    @Test void clarificationReplayAtomicallyRebindsSessionAndChallengeToken() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        byte[] oldTokenHash = new byte[32];
        byte[] newTokenHash = new byte[32];
        newTokenHash[0] = 1;
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session oldSession =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, oldTokenHash, now, now.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess oldAccess =
                TurnExecutionStore.SessionAccess.tentative(oldSession);
        store.claim(requestId, conversationId, RequestFingerprintSet.single(fingerprint),
                oldAccess, now, Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        String clarificationId = "clarification_replay_rebind_1";
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_size", "数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                conversationId, oldTokenHash, "public-1", challenge,
                java.util.Map.of("field_size", java.util.Map.of("choice_size_2", "size:2")),
                java.util.Map.of(), BlockedGoalTemplate.recommendation(
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));
        assertThat(store.complete(
                requestId, fingerprint,
                new PublicAgentTurn.Clarification(requestId, "补充", challenge, List.of()),
                List.of(), List.of(record), oldSession, oldAccess, now.plusSeconds(1),
                deadline(Duration.ofSeconds(5)))).isTrue();
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session newSession =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, newTokenHash, now.plusSeconds(2),
                        now.plus(Duration.ofMinutes(30)).plusSeconds(2));
        TurnExecutionStore.ClaimResult replay = store.claim(
                requestId, conversationId, RequestFingerprintSet.single(fingerprint),
                TurnExecutionStore.SessionAccess.tentative(newSession), now.plusSeconds(2),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));

        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(store.find(requestId).orElseThrow().getChallenges())
                .singleElement().satisfies(rebound ->
                assertThat(rebound.resumeTokenHash()).containsExactly(newTokenHash));
        assertThat(store.consumeClarification(
                clarificationId, conversationId, oldTokenHash, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2"),
                now.plusSeconds(3), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(ClarificationStore.Status.UNAUTHORIZED);
        assertThat(store.consumeClarification(
                clarificationId, conversationId, newTokenHash, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2"),
                now.plusSeconds(3), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(ClarificationStore.Status.CONSUMED);
        assertThat(findSession(sessions(), oldTokenHash, now.plusSeconds(3))).isEmpty();
        assertThat(findSession(sessions(), newTokenHash, now.plusSeconds(3))).isPresent();
    }

    @Test void replayAndContextExpireAtTheExactAbsoluteBoundaryWithoutReadExtension() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        String contextHandle = "context_absolute_ttl_1";
        com.portfolio.agent.turn.continuation.ProjectDiscussionContext context =
                new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                contextHandle, conversationId, "public-1", now.plus(Duration.ofMinutes(30)),
                "project-a", Set.of("project-a"), now, null);
        assertThat(claim(store, requestId, conversationId, fingerprint, now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        assertThat(complete(store,
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "完成", List.of()),
                List.of(context), List.of(), null, now,
                deadline(Duration.ofSeconds(5)))).isTrue();

        assertThat(store.findContext(
                conversationId, contextHandle, now.plus(Duration.ofMinutes(29)),
                deadline(Duration.ofSeconds(5)))).isPresent();
        assertThat(store.findContext(
                conversationId, contextHandle, now.plus(Duration.ofMinutes(29)),
                deadline(Duration.ofSeconds(5)))).isPresent();
        assertThat(store.findContext(
                conversationId, contextHandle, now.plus(Duration.ofMinutes(30)),
                deadline(Duration.ofSeconds(5)))).isEmpty();
        assertThat(claim(store,
                requestId, conversationId, fingerprint, now.plus(Duration.ofMinutes(29)),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(claim(store,
                requestId, conversationId, fingerprint, now.plus(Duration.ofMinutes(30)),
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
    }

    @Test void clarificationExpiresAtFiveMinutesAndCannotBeExtendedByReads() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        byte[] tokenHash = new byte[32];
        String clarificationId = "clarification_exact_ttl_1";
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_size", "数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                conversationId, tokenHash, "public-1", challenge,
                java.util.Map.of("field_size", java.util.Map.of("choice_size_2", "size:2")),
                java.util.Map.of(), BlockedGoalTemplate.recommendation(
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));
        claim(store, requestId, conversationId, fingerprint, now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));
        assertThat(complete(store,
                requestId, fingerprint,
                new PublicAgentTurn.Clarification(requestId, "补充", challenge, List.of()),
                List.of(), List.of(record), null, now,
                deadline(Duration.ofSeconds(5)))).isTrue();

        ClarificationStore.ConsumeResult expired = store.consumeClarification(
                clarificationId, conversationId, tokenHash, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2"),
                now.plus(Duration.ofMinutes(5)), deadline(Duration.ofSeconds(5)));

        assertThat(expired.status()).isEqualTo(ClarificationStore.Status.EXPIRED);
    }

    @Test void sessionRotationPreservesOriginalThirtyMinuteExpiry() {
        JdbcConversationSessionStore sessions = new JdbcConversationSessionStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                "agent_context", "token-key-1", Set.of("token-key-1"));
        String conversationId = UUID.randomUUID().toString();
        byte[] firstHash = new byte[32];
        byte[] rotatedHash = new byte[32];
        rotatedHash[0] = 1;
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, firstHash, now, now.plus(Duration.ofMinutes(30))));
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, rotatedHash, now.plus(Duration.ofMinutes(10)),
                now.plus(Duration.ofMinutes(40))));

        assertThat(findSession(sessions, firstHash, now.plus(Duration.ofMinutes(11)))).isEmpty();
        assertThat(findSession(sessions, rotatedHash, now.plus(Duration.ofMinutes(29)))).isPresent();
        assertThat(findSession(sessions, rotatedHash, now.plus(Duration.ofMinutes(30)))).isEmpty();
        java.time.OffsetDateTime absolute = jdbc.queryForObject(
                "SELECT absolute_expires_at FROM agent_context.conversation_session WHERE conversation_id=?",
                java.time.OffsetDateTime.class, UUID.fromString(conversationId));
        assertThat(absolute.toInstant()).isEqualTo(now.plus(Duration.ofMinutes(30)));
    }

    @Test void settlementRollsBackAllRowsWhenAChildInsertFails() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        com.portfolio.agent.turn.continuation.ProjectDiscussionContext context =
                new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                "context_duplicate_1", conversationId, "public-1",
                now.plus(Duration.ofMinutes(30)), "project-a",
                Set.of("project-a"), now, null);
        claim(store, requestId, conversationId, fingerprint, now,
                Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));

        assertThatThrownBy(() -> complete(store,
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "不会部分提交", List.of()),
                List.of(context, context), List.of(), null, now,
                deadline(Duration.ofSeconds(5))))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_context.agent_turn_execution WHERE request_id=?",
                String.class, requestId)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_context WHERE source_request_id=?",
                Integer.class, requestId)).isZero();
    }

    @Test void atomicClearWinsAgainstConcurrentAuthenticatedClaim() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        JdbcConversationSessionStore sessions = sessions();
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30))));
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.authenticated(conversationId, tokenHash);

        runRace(
                () -> store.claim(requestId, conversationId,
                        RequestFingerprintSet.single(fingerprint), access, now.plusSeconds(1),
                        Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))),
                () -> store.clearConversation(conversationId, tokenHash, now.plusSeconds(1)));

        assertConversationCleared(conversationId);
    }

    @Test void atomicClearWinsAgainstConcurrentSettlement() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        sessions().save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30))));
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.authenticated(conversationId, tokenHash);
        store.claim(requestId, conversationId, RequestFingerprintSet.single(fingerprint),
                access, now, Duration.ofSeconds(35), deadline(Duration.ofSeconds(5)));

        runRace(
                () -> store.complete(requestId, fingerprint,
                        new PublicAgentTurn.Conversational(requestId, "竞态终局", List.of()),
                        List.of(), List.of(), null, access, now.plusSeconds(1),
                        deadline(Duration.ofSeconds(5))),
                () -> store.clearConversation(conversationId, tokenHash, now.plusSeconds(1)));

        assertConversationCleared(conversationId);
    }

    @Test void newerTentativeTurnCanReuseRequestIdAfterAuthorizedClear() {
        String conversationId = UUID.randomUUID().toString();
        byte[] oldTokenHash = new byte[32];
        byte[] newTokenHash = new byte[32];
        newTokenHash[0] = 1;
        JdbcConversationSessionStore sessions = sessions();
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, oldTokenHash, now, now.plus(Duration.ofMinutes(30))));
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        assertThat(store.clearConversation(
                conversationId, oldTokenHash, now.plusSeconds(1))).isTrue();
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session replacement =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, newTokenHash, now.plusSeconds(2),
                        now.plus(Duration.ofMinutes(30)).plusSeconds(2));
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.tentative(replacement);

        assertThat(store.claim(
                requestId, conversationId, RequestFingerprintSet.single(fingerprint), access,
                now.plusSeconds(2), Duration.ofSeconds(35), deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CANCELLED);
        replacement = new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, newTokenHash, now.plus(Duration.ofMinutes(30)),
                now.plus(Duration.ofMinutes(60)));
        access = TurnExecutionStore.SessionAccess.tentative(replacement);
        assertThat(store.claim(
                requestId, conversationId, RequestFingerprintSet.single(fingerprint), access,
                now.plus(Duration.ofMinutes(30)), Duration.ofSeconds(35),
                deadline(Duration.ofSeconds(5))).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        assertThat(store.complete(
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "新会话", List.of()),
                List.of(), List.of(), replacement, access,
                now.plus(Duration.ofMinutes(30)).plusSeconds(1),
                deadline(Duration.ofSeconds(5)))).isTrue();
        assertThat(findSession(sessions, oldTokenHash, now.plus(Duration.ofMinutes(30)))).isEmpty();
        assertThat(findSession(sessions, newTokenHash, now.plus(Duration.ofMinutes(31)))).isPresent();
        java.time.OffsetDateTime expiry = jdbc.queryForObject(
                "SELECT absolute_expires_at FROM agent_context.conversation_session WHERE conversation_id=?",
                java.time.OffsetDateTime.class, UUID.fromString(conversationId));
        assertThat(expiry.toInstant()).isEqualTo(replacement.expiresAt());
    }

    private JdbcConversationSessionStore sessions() {
        return new JdbcConversationSessionStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                "agent_context", "token-key-1", Set.of("token-key-1"),
                Duration.ofSeconds(3), Clock.fixed(now, ZoneOffset.UTC));
    }

    private JdbcAgentStateStore storeWithFingerprintKeys(
            String currentKeyId, Set<String> supportedKeyIds) {
        return new JdbcAgentStateStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new AgentStatePayloadCodec(
                        JsonMapper.builder().addModule(new ParameterNamesModule())
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .build(), "state-key-1", new byte[32]),
                "agent_context", Duration.ofMinutes(30), Duration.ofMinutes(5),
                "token-key-1", Set.of("token-key-1"), currentKeyId, supportedKeyIds,
                Duration.ofSeconds(3), 500, Clock.fixed(now, ZoneOffset.UTC));
    }

    private byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private java.util.Optional<com.portfolio.agent.turn.continuation.ConversationSessionStore.Session>
            findSession(JdbcConversationSessionStore sessions, byte[] hash, Instant at) {
        return sessions.find(List.of(hash), at, deadline(Duration.ofSeconds(5)));
    }

    private void runRace(
            java.util.concurrent.Callable<?> first,
            java.util.concurrent.Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> firstResult = executor.submit(() -> { start.await(); return first.call(); });
            Future<?> secondResult = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            firstResult.get();
            secondResult.get();
        }
    }

    private void assertConversationCleared(String conversationId) {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_context.agent_turn_execution WHERE conversation_id=?",
                Integer.class, UUID.fromString(conversationId))).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM agent_context.conversation_session WHERE conversation_id=?",
                Boolean.class, UUID.fromString(conversationId))).isTrue();
    }

    private TurnDeadline deadline(Duration duration) {
        return TurnDeadline.after(duration, Clock.systemUTC());
    }

    private TurnExecutionStore.ClaimResult claim(
            JdbcAgentStateStore target, UUID requestId, String conversationId,
            byte[] fingerprint, Instant at, Duration lease, TurnDeadline deadline) {
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                testSession(requestId, conversationId, at);
        return target.claim(requestId, conversationId,
                RequestFingerprintSet.single(fingerprint),
                TurnExecutionStore.SessionAccess.tentative(session), at, lease, deadline);
    }

    private TurnExecutionStore.ClaimResult claim(
            JdbcAgentStateStore target, UUID requestId, String conversationId,
            RequestFingerprintSet fingerprints, TurnExecutionStore.SessionAccess access,
            Instant at, Duration lease, TurnDeadline deadline) {
        return target.claim(requestId, conversationId, fingerprints, access, at, lease, deadline);
    }

    private boolean complete(
            JdbcAgentStateStore target, UUID requestId, byte[] fingerprint,
            PublicAgentTurn snapshot, List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            com.portfolio.agent.turn.continuation.ConversationSessionStore.Session ignored,
            Instant at, TurnDeadline deadline) {
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                testSessions.get(requestId);
        return target.complete(requestId, fingerprint, snapshot, contexts, challenges,
                session, TurnExecutionStore.SessionAccess.tentative(session), at, deadline);
    }

    private com.portfolio.agent.turn.continuation.ConversationSessionStore.Session testSession(
            UUID requestId, String conversationId, Instant at) {
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, java.util.Arrays.copyOf(
                        (at.toString() + requestId).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8), 32),
                        at, at.plus(Duration.ofMinutes(30)));
        testSessions.put(requestId, session);
        return session;
    }

    private TurnExecutionStore.SessionAccess testAccess(
            UUID requestId, String conversationId, Instant at) {
        return TurnExecutionStore.SessionAccess.tentative(
                testSession(requestId, conversationId, at));
    }
}
