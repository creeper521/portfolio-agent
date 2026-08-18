package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL claim/terminal/replay authority; settlement is one encrypted row update. */
public final class JdbcAgentStateStore implements AgentStateStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AgentStatePayloadCodec codec;
    private final String table;
    private final Duration absoluteTtl;
    private final String sessionTable;
    private final String contextTable;
    private final String clarificationTable;
    private final String tokenKeyId;

    public JdbcAgentStateStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            AgentStatePayloadCodec codec, String schema, Duration absoluteTtl,
            String tokenKeyId) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.codec = java.util.Objects.requireNonNull(codec);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("schema is invalid");
        }
        this.table = schema + ".agent_turn_execution";
        this.sessionTable = schema + ".conversation_session";
        this.contextTable = schema + ".agent_turn_context";
        this.clarificationTable = schema + ".agent_turn_clarification";
        if (tokenKeyId == null || tokenKeyId.isBlank()) {
            throw new IllegalArgumentException("tokenKeyId is required");
        }
        this.tokenKeyId = tokenKeyId;
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("absoluteTtl is invalid");
        }
        this.absoluteTtl = absoluteTtl;
    }

    @Override public ClaimResult claim(
            UUID requestId, String conversationId, byte[] fingerprint,
            Instant now, Duration leaseDuration) {
        return transactions.execute(status -> {
            Row row = select(requestId, true).orElse(null);
            if (row == null) {
                jdbc.update("INSERT INTO " + table + " (request_id, conversation_id, request_fingerprint, status, lease_expires_at, created_at, updated_at, absolute_expires_at) VALUES (?,?,?,?,?,?,?,?)",
                        requestId, UUID.fromString(conversationId), fingerprint, "CLAIMED",
                        time(now.plus(leaseDuration)), time(now), time(now), time(now.plus(absoluteTtl)));
                return ClaimResult.claimed();
            }
            if (!row.conversationId().toString().equals(conversationId)
                    || !MessageDigest.isEqual(row.fingerprint(), fingerprint)) {
                return ClaimResult.state(ClaimResult.Status.CONFLICT);
            }
            if (row.status() == TurnExecutionRecord.Status.COMPLETED) {
                return ClaimResult.replay(payload(row).publicTurn());
            }
            if (row.status() == TurnExecutionRecord.Status.CANCELLED) {
                return ClaimResult.state(ClaimResult.Status.CANCELLED);
            }
            if (now.isBefore(row.leaseExpiresAt())) {
                return ClaimResult.inProgress(Math.max(
                        1, Duration.between(now, row.leaseExpiresAt()).toSeconds()));
            }
            jdbc.update("UPDATE " + table + " SET lease_expires_at=?, updated_at=? WHERE request_id=?",
                    time(now.plus(leaseDuration)), time(now), requestId);
            return ClaimResult.claimed();
        });
    }

    @Override public boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate, Instant completedAt) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Row row = select(requestId, true).orElse(null);
            if (row == null || row.status() != TurnExecutionRecord.Status.CLAIMED
                    || !MessageDigest.isEqual(row.fingerprint(), fingerprint)) return false;
            AgentStatePayloadCodec.Envelope envelope = codec.encode(
                    requestId, row.conversationId().toString(),
                    new AgentStatePayloadCodec.SettlementPayload(snapshot, contexts, challenges));
            if (sessionToCreate != null) {
                jdbc.update("INSERT INTO " + sessionTable + " (conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision) VALUES (?,?,?,?,?,?,?,?,?,0) ON CONFLICT (resume_token_hash) DO NOTHING",
                        UUID.fromString(sessionToCreate.conversationId()), sessionToCreate.tokenHash(),
                        tokenKeyId, time(sessionToCreate.createdAt()), time(sessionToCreate.createdAt()),
                        time(sessionToCreate.expiresAt()), time(sessionToCreate.expiresAt()), 0, 0);
            }
            for (ContinuationContext context : contexts) {
                AgentStatePayloadCodec.Envelope contextEnvelope = codec.encodeContext(
                        requestId, row.conversationId().toString(), context);
                jdbc.update("INSERT INTO " + contextTable + " (conversation_id, context_handle, source_request_id, expires_at, payload_key_id, payload_nonce, payload_ciphertext) VALUES (?,?,?,?,?,?,?)",
                        row.conversationId(), context.getContextHandle(), requestId,
                        time(context.getExpiresAt()), contextEnvelope.keyId(),
                        contextEnvelope.nonce(), contextEnvelope.ciphertext());
            }
            for (ClarificationStore.Record challenge : challenges) {
                AgentStatePayloadCodec.Envelope challengeEnvelope = codec.encodeChallenge(
                        requestId, row.conversationId().toString(), challenge);
                jdbc.update("INSERT INTO " + clarificationTable + " (clarification_id, conversation_id, source_request_id, resume_token_hash, content_release_id, expires_at, consumed, payload_key_id, payload_nonce, payload_ciphertext) VALUES (?,?,?,?,?,?,false,?,?,?)",
                        challenge.challenge().getClarificationId(), row.conversationId(), requestId,
                        challenge.resumeTokenHash(), challenge.contentReleaseId(),
                        time(completedAt.plus(Duration.ofMinutes(5))), challengeEnvelope.keyId(),
                        challengeEnvelope.nonce(), challengeEnvelope.ciphertext());
            }
            return jdbc.update("UPDATE " + table + " SET status='COMPLETED', settlement_key_id=?, settlement_nonce=?, settlement_ciphertext=?, updated_at=?, terminal_at=? WHERE request_id=? AND status='CLAIMED'",
                    envelope.keyId(), envelope.nonce(), envelope.ciphertext(),
                    time(completedAt), time(completedAt), requestId) == 1;
        }));
    }

    @Override public boolean cancel(UUID requestId, String conversationId, Instant cancelledAt) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Row row = select(requestId, true).orElse(null);
            if (row == null || !row.conversationId().toString().equals(conversationId)
                    || row.status() != TurnExecutionRecord.Status.CLAIMED) return false;
            return jdbc.update("UPDATE " + table + " SET status='CANCELLED', updated_at=?, terminal_at=? WHERE request_id=? AND status='CLAIMED'",
                    time(cancelledAt), time(cancelledAt), requestId) == 1;
        }));
    }

    @Override public Optional<TurnExecutionRecord> find(UUID requestId) {
        return select(requestId, false).map(row -> {
            AgentStatePayloadCodec.SettlementPayload payload =
                    row.status() == TurnExecutionRecord.Status.COMPLETED ? payload(row) : null;
            return TurnExecutionRecord.restore(
                    row.requestId(), row.conversationId().toString(), row.fingerprint(), row.status(),
                    row.leaseExpiresAt(), payload == null ? null : payload.publicTurn(),
                    payload == null ? List.of() : payload.contexts(),
                    payload == null ? List.of() : payload.challenges(), row.terminalAt());
        });
    }

    @Override public void clearConversation(String conversationId) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM " + table + " WHERE conversation_id=?", UUID.fromString(conversationId)));
    }

    @Override public Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT source_request_id, payload_key_id, payload_nonce, payload_ciphertext FROM "
                            + contextTable + " WHERE conversation_id=? AND context_handle=? AND expires_at>?",
                    (result, index) -> codec.decodeContext(
                            result.getObject("source_request_id", UUID.class), conversationId,
                            contextHandle, new AgentStatePayloadCodec.Envelope(
                            result.getString("payload_key_id"), result.getBytes("payload_nonce"),
                            result.getBytes("payload_ciphertext"))),
                    UUID.fromString(conversationId), contextHandle, time(now)));
        } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }

    @Override public ClarificationStore.ConsumeResult consumeClarification(
            String clarificationId, String conversationId, byte[] tokenHash,
            String currentReleaseId, ClarificationStore.ClarificationAnswer answer,
            Instant now) {
        return transactions.execute(status -> {
            ChallengeRow row;
            try {
                row = jdbc.queryForObject(
                        "SELECT source_request_id, resume_token_hash, content_release_id, expires_at, consumed, payload_key_id, payload_nonce, payload_ciphertext FROM "
                                + clarificationTable + " WHERE clarification_id=? FOR UPDATE",
                        (result, index) -> challengeRow(result), clarificationId);
            } catch (EmptyResultDataAccessException missing) {
                return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.NOT_FOUND);
            }
            if (row.consumed()) return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.ALREADY_CONSUMED);
            if (!now.isBefore(row.expiresAt())) return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.EXPIRED);
            if (!MessageDigest.isEqual(row.tokenHash(), tokenHash)) {
                return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.UNAUTHORIZED);
            }
            if (!row.contentReleaseId().equals(currentReleaseId)) {
                return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.STALE_RELEASE);
            }
            ClarificationStore.Record record = codec.decodeChallenge(
                    row.sourceRequestId(), conversationId, clarificationId, row.envelope());
            ClarificationStore validator = new ClarificationStore(
                    java.time.Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(1));
            validator.save(record);
            ClarificationStore.ConsumeResult consumed = validator.consume(
                    clarificationId, conversationId, tokenHash, currentReleaseId, answer);
            if (consumed.status() == ClarificationStore.Status.CONSUMED) {
                jdbc.update("UPDATE " + clarificationTable
                        + " SET consumed=true WHERE clarification_id=?", clarificationId);
            }
            return consumed;
        });
    }

    private Optional<Row> select(UUID requestId, boolean lock) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT request_id, conversation_id, request_fingerprint, status, lease_expires_at, settlement_key_id, settlement_nonce, settlement_ciphertext, terminal_at FROM "
                            + table + " WHERE request_id=?" + (lock ? " FOR UPDATE" : ""),
                    (result, index) -> row(result), requestId));
        } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }
    private Row row(ResultSet result) throws SQLException {
        OffsetDateTime terminal = result.getObject("terminal_at", OffsetDateTime.class);
        return new Row(
                result.getObject("request_id", UUID.class),
                result.getObject("conversation_id", UUID.class),
                result.getBytes("request_fingerprint"),
                TurnExecutionRecord.Status.valueOf(result.getString("status")),
                result.getObject("lease_expires_at", OffsetDateTime.class).toInstant(),
                result.getString("settlement_key_id"), result.getBytes("settlement_nonce"),
                result.getBytes("settlement_ciphertext"), terminal == null ? null : terminal.toInstant());
    }
    private AgentStatePayloadCodec.SettlementPayload payload(Row row) {
        return codec.decode(row.requestId(), row.conversationId().toString(),
                new AgentStatePayloadCodec.Envelope(
                        row.keyId(), row.nonce(), row.ciphertext()));
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private ChallengeRow challengeRow(ResultSet result) throws SQLException {
        return new ChallengeRow(
                result.getObject("source_request_id", UUID.class),
                result.getBytes("resume_token_hash"), result.getString("content_release_id"),
                result.getObject("expires_at", OffsetDateTime.class).toInstant(),
                result.getBoolean("consumed"), new AgentStatePayloadCodec.Envelope(
                result.getString("payload_key_id"), result.getBytes("payload_nonce"),
                result.getBytes("payload_ciphertext")));
    }
    private record Row(
            UUID requestId, UUID conversationId, byte[] fingerprint,
            TurnExecutionRecord.Status status, Instant leaseExpiresAt,
            String keyId, byte[] nonce, byte[] ciphertext, Instant terminalAt) { }
    private record ChallengeRow(
            UUID sourceRequestId, byte[] tokenHash, String contentReleaseId,
            Instant expiresAt, boolean consumed, AgentStatePayloadCodec.Envelope envelope) { }
}
