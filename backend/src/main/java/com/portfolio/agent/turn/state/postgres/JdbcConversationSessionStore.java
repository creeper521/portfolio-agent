package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcConversationSessionStore implements ConversationSessionStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String table;
    private final String tokenKeyId;
    private final Set<String> supportedTokenKeyIds;
    private final Clock clock;
    private final Duration databaseOperationTimeout;
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId) {
        this(jdbc, transactions, schema, tokenKeyId, Set.of(tokenKeyId));
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds) {
        this(jdbc, transactions, schema, tokenKeyId, supportedTokenKeyIds, Clock.systemUTC());
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds,
            Clock clock) {
        this(jdbc, transactions, schema, tokenKeyId, supportedTokenKeyIds,
                Duration.ofSeconds(3), clock);
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds,
            Duration databaseOperationTimeout, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions;
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("schema is invalid");
        }
        if (tokenKeyId == null || tokenKeyId.isBlank()) {
            throw new IllegalArgumentException("tokenKeyId is required");
        }
        this.table = schema + ".conversation_session";
        this.tokenKeyId = tokenKeyId;
        this.supportedTokenKeyIds = Set.copyOf(supportedTokenKeyIds);
        if (!this.supportedTokenKeyIds.contains(tokenKeyId)) {
            throw new IllegalArgumentException("current token key must be supported");
        }
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (databaseOperationTimeout == null || databaseOperationTimeout.isZero()
                || databaseOperationTimeout.isNegative()) {
            throw new IllegalArgumentException("databaseOperationTimeout is invalid");
        }
        this.databaseOperationTimeout = databaseOperationTimeout;
    }
    @Override public Optional<Session> find(
            java.util.List<byte[]> tokenHashes, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            try {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT conversation_id, resume_token_hash, created_at,"
                            + " absolute_expires_at, active_discussion_handle,"
                            + " active_discussion_project_id,"
                            + " active_discussion_expires_at FROM " + table
                            + " WHERE resume_token_hash IN (" + placeholders(tokenHashes.size())
                            + ") AND revoked_at IS NULL"
                            + " AND absolute_expires_at>? AND token_key_id IN ("
                            + placeholders(supportedTokenKeyIds.size()) + ")",
                    (result, index) -> {
                        OffsetDateTime discussionExpiry = result.getObject(
                                "active_discussion_expires_at",
                                OffsetDateTime.class);
                        com.portfolio.agent.turn.continuation.ActiveDiscussionPointer pointer =
                                discussionExpiry == null ? null
                                        : new com.portfolio.agent.turn.continuation.ActiveDiscussionPointer(
                                        result.getString(
                                                "active_discussion_handle"),
                                        result.getString(
                                                "active_discussion_project_id"),
                                        discussionExpiry.toInstant());
                        return new Session(
                                result.getObject(
                                        "conversation_id", UUID.class)
                                        .toString(),
                                result.getBytes("resume_token_hash"),
                                result.getObject(
                                        "created_at", OffsetDateTime.class)
                                        .toInstant(),
                                result.getObject(
                                        "absolute_expires_at",
                                        OffsetDateTime.class).toInstant(),
                                pointer);
                    },
                        findParameters(tokenHashes, time(now), supportedTokenKeyIds)));
            } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
        });
    }
    @Override public void save(Session session) {
        transactions.executeWithoutResult(status -> {
            applyDatabaseTimeout();
            jdbc.update(
                "INSERT INTO " + table + " AS existing (conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision, revoked_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,0,NULL) ON CONFLICT (conversation_id) DO UPDATE SET "
                        + "resume_token_hash=EXCLUDED.resume_token_hash, token_key_id=EXCLUDED.token_key_id, "
                        + "created_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.created_at ELSE existing.created_at END, "
                        + "last_accessed_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.created_at ELSE existing.last_accessed_at END, "
                        + "idle_expires_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END, "
                        + "absolute_expires_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END, "
                        + "revoked_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN NULL ELSE existing.revoked_at END "
                        + "WHERE (existing.revoked_at IS NULL AND existing.absolute_expires_at>EXCLUDED.created_at) OR (existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash)",
                UUID.fromString(session.conversationId()), session.tokenHash(), tokenKeyId,
                time(session.createdAt()), time(session.createdAt()), time(session.expiresAt()),
                    time(session.expiresAt()), 0, 0);
        });
    }
    void revokeForTest(String conversationId) {
        transactions.executeWithoutResult(status -> {
            applyDatabaseTimeout();
            jdbc.update("UPDATE " + table
                            + " SET revoked_at=? WHERE conversation_id=? AND revoked_at IS NULL",
                    time(clock.instant()), UUID.fromString(conversationId));
        });
    }
    private void applyDatabaseTimeout() {
        jdbc.execute("SET LOCAL statement_timeout = " + databaseOperationTimeout.toMillis());
    }
    private void applyDatabaseTimeout(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long timeoutMillis = Math.min(
                databaseOperationTimeout.toMillis(), deadline.remainingMillis());
        if (timeoutMillis < 1) throw new IllegalStateException("session lookup deadline exceeded");
        jdbc.execute("SET LOCAL statement_timeout = " + timeoutMillis);
    }
    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
    private Object[] parameters(Object first, Object second, Set<String> remaining) {
        Object[] values = new Object[2 + remaining.size()];
        values[0] = first;
        values[1] = second;
        int index = 2;
        for (String value : remaining) values[index++] = value;
        return values;
    }
    private Object[] findParameters(
            java.util.List<byte[]> hashes, Object now, Set<String> keyIds) {
        Object[] values = new Object[hashes.size() + 1 + keyIds.size()];
        int index = 0;
        for (byte[] hash : hashes) values[index++] = hash;
        values[index++] = now;
        for (String keyId : keyIds) values[index++] = keyId;
        return values;
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
