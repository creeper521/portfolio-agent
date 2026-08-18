package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

public final class JdbcConversationSessionStore implements ConversationSessionStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String table;
    private final String tokenKeyId;
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId) {
        this.jdbc = jdbc; this.transactions = transactions;
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("schema is invalid");
        }
        if (tokenKeyId == null || tokenKeyId.isBlank()) {
            throw new IllegalArgumentException("tokenKeyId is required");
        }
        this.table = schema + ".conversation_session";
        this.tokenKeyId = tokenKeyId;
    }
    @Override public Optional<Session> find(byte[] tokenHash, Instant now) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT conversation_id, created_at, absolute_expires_at FROM " + table
                            + " WHERE resume_token_hash=? AND idle_expires_at>? AND absolute_expires_at>?",
                    (result, index) -> new Session(
                            result.getObject("conversation_id", UUID.class).toString(), tokenHash,
                            result.getObject("created_at", OffsetDateTime.class).toInstant(),
                            result.getObject("absolute_expires_at", OffsetDateTime.class).toInstant()),
                    tokenHash, time(now), time(now)));
        } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }
    @Override public void save(Session session) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO " + table + " (conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision) VALUES (?,?,?,?,?,?,?,?,?,0) ON CONFLICT (resume_token_hash) DO NOTHING",
                UUID.fromString(session.conversationId()), session.tokenHash(), tokenKeyId,
                time(session.createdAt()), time(session.createdAt()), time(session.expiresAt()),
                time(session.expiresAt()), 0, 0));
    }
    @Override public void revoke(String conversationId) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM " + table + " WHERE conversation_id=?", UUID.fromString(conversationId)));
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
