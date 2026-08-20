package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintSet;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final Set<String> supportedTokenKeyIds;
    private final String currentFingerprintKeyId;
    private final Set<String> supportedFingerprintKeyIds;
    private final Duration databaseOperationTimeout;
    private final Duration challengeTtl;
    private final int cleanupBatchSize;
    private final Clock clock;

    public JdbcAgentStateStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            AgentStatePayloadCodec codec, String schema, Duration absoluteTtl,
            String tokenKeyId, Duration databaseOperationTimeout) {
        this(jdbc, transactions, codec, schema, absoluteTtl, Duration.ofMinutes(5),
                tokenKeyId, Set.of(tokenKeyId), "test-current", Set.of("test-current"),
                databaseOperationTimeout, 500,
                Clock.systemUTC());
    }

    public JdbcAgentStateStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            AgentStatePayloadCodec codec, String schema, Duration absoluteTtl,
            Duration challengeTtl, String tokenKeyId, Set<String> supportedTokenKeyIds,
            String currentFingerprintKeyId, Set<String> supportedFingerprintKeyIds,
            Duration databaseOperationTimeout, int cleanupBatchSize, Clock clock) {
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
        this.supportedTokenKeyIds = Set.copyOf(
                java.util.Objects.requireNonNull(supportedTokenKeyIds, "supportedTokenKeyIds"));
        if (!this.supportedTokenKeyIds.contains(tokenKeyId)) {
            throw new IllegalArgumentException("current token key must be supported");
        }
        if (currentFingerprintKeyId == null || currentFingerprintKeyId.isBlank()) {
            throw new IllegalArgumentException("currentFingerprintKeyId is required");
        }
        this.currentFingerprintKeyId = currentFingerprintKeyId;
        this.supportedFingerprintKeyIds = Set.copyOf(supportedFingerprintKeyIds);
        if (!this.supportedFingerprintKeyIds.contains(currentFingerprintKeyId)) {
            throw new IllegalArgumentException("current fingerprint key must be supported");
        }
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("absoluteTtl is invalid");
        }
        this.absoluteTtl = absoluteTtl;
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()
                || challengeTtl.compareTo(absoluteTtl) > 0) {
            throw new IllegalArgumentException("challengeTtl is invalid");
        }
        this.challengeTtl = challengeTtl;
        if (databaseOperationTimeout == null || databaseOperationTimeout.isZero()
                || databaseOperationTimeout.isNegative()) {
            throw new IllegalArgumentException("databaseOperationTimeout is invalid");
        }
        this.databaseOperationTimeout = databaseOperationTimeout;
        if (cleanupBatchSize < 1 || cleanupBatchSize > 500) {
            throw new IllegalArgumentException("cleanupBatchSize is invalid");
        }
        this.cleanupBatchSize = cleanupBatchSize;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public ClaimResult claim(
            UUID requestId, String conversationId, RequestFingerprintSet fingerprints,
            SessionAccess sessionAccess, Instant now, Duration leaseDuration,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        requireDatabaseTime(deadline);
        if (!currentFingerprintKeyId.equals(fingerprints.currentKeyId())) {
            throw new IllegalArgumentException("fingerprint current key id does not match Store");
        }
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            if (!authorizeClaimSession(conversationId, sessionAccess, now)) {
                return ClaimResult.state(ClaimResult.Status.CANCELLED);
            }
            Row row = select(requestId, true).orElse(null);
            if (row != null && !now.isBefore(row.absoluteExpiresAt())) {
                applyDatabaseTimeout(deadline);
                jdbc.update("DELETE FROM " + table + " WHERE request_id=?", requestId);
                row = null;
            }
            if (row == null) {
                insertClaim(requestId, conversationId, fingerprints.current(), now, leaseDuration, deadline);
                return ClaimResult.claimed();
            }
            if (!row.conversationId().toString().equals(conversationId)
                    || !fingerprints.matches(row.fingerprint())) {
                return ClaimResult.state(ClaimResult.Status.CONFLICT);
            }
            if (row.status() == TurnExecutionRecord.Status.COMPLETED) {
                if (sessionAccess.tentativeSession() != null) {
                    rotateReplaySession(
                            conversationId, sessionAccess.tentativeSession(), now, deadline);
                }
                if (!MessageDigest.isEqual(row.fingerprint(), fingerprints.current())) {
                    applyDatabaseTimeout(deadline);
                    jdbc.update("UPDATE " + table
                                    + " SET request_fingerprint=?, fingerprint_key_id=?,"
                                    + " updated_at=? WHERE request_id=?",
                            fingerprints.current(), fingerprints.currentKeyId(),
                            time(now), requestId);
                }
                return ClaimResult.replay(payload(row).publicTurn());
            }
            if (row.status() == TurnExecutionRecord.Status.CANCELLED) {
                return ClaimResult.state(ClaimResult.Status.CANCELLED);
            }
            if (now.isBefore(row.leaseExpiresAt())) {
                return ClaimResult.inProgress(Math.max(
                        1, Duration.between(now, row.leaseExpiresAt()).toSeconds()));
            }
            applyDatabaseTimeout(deadline);
            jdbc.update("UPDATE " + table + " SET request_fingerprint=?,"
                            + " fingerprint_key_id=?, lease_expires_at=?, updated_at=?"
                            + " WHERE request_id=?",
                    fingerprints.current(), fingerprints.currentKeyId(),
                    time(now.plus(leaseDuration)), time(now), requestId);
            return ClaimResult.claimed();
        });
    }

    @Override public boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate, SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        requireDatabaseTime(deadline);
        return Boolean.TRUE.equals(transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            if (!authorizeSettlementSession(
                    sessionAccess, sessionToCreate, completedAt)) return false;
            Row row = select(requestId, true).orElse(null);
            if (row == null || row.status() != TurnExecutionRecord.Status.CLAIMED
                    || !MessageDigest.isEqual(row.fingerprint(), fingerprint)
                    || !completedAt.isBefore(row.absoluteExpiresAt())) return false;
            AgentStatePayloadCodec.Envelope envelope = codec.encode(
                    requestId, row.conversationId().toString(),
                    new AgentStatePayloadCodec.SettlementPayload(snapshot, contexts, challenges));
            if (sessionToCreate != null) {
                applyDatabaseTimeout(deadline);
                jdbc.update("INSERT INTO " + sessionTable + " AS existing (conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision, revoked_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,0,NULL) ON CONFLICT (conversation_id) DO UPDATE SET "
                                + "resume_token_hash=EXCLUDED.resume_token_hash, token_key_id=EXCLUDED.token_key_id, "
                                + "created_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.created_at ELSE existing.created_at END, "
                                + "last_accessed_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.created_at ELSE existing.last_accessed_at END, "
                                + "idle_expires_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END, "
                                + "absolute_expires_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END, "
                                + "revoked_at=CASE WHEN existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash THEN NULL ELSE existing.revoked_at END "
                                + "WHERE (existing.revoked_at IS NULL AND existing.absolute_expires_at>EXCLUDED.created_at) OR (existing.absolute_expires_at<=EXCLUDED.created_at AND existing.resume_token_hash<>EXCLUDED.resume_token_hash)",
                        UUID.fromString(sessionToCreate.conversationId()), sessionToCreate.tokenHash(),
                        tokenKeyId, time(sessionToCreate.createdAt()), time(sessionToCreate.createdAt()),
                        time(sessionToCreate.expiresAt()), time(sessionToCreate.expiresAt()), 0, 0);
            }
            for (ContinuationContext context : contexts) {
                AgentStatePayloadCodec.Envelope contextEnvelope = codec.encodeContext(
                        requestId, row.conversationId().toString(), context);
                applyDatabaseTimeout(deadline);
                jdbc.update("INSERT INTO " + contextTable + " (conversation_id, context_handle, source_request_id, expires_at, payload_key_id, payload_nonce, payload_ciphertext) VALUES (?,?,?,?,?,?,?)",
                        row.conversationId(), context.getContextHandle(), requestId,
                        time(earlier(context.getExpiresAt(), completedAt.plus(absoluteTtl))), contextEnvelope.keyId(),
                        contextEnvelope.nonce(), contextEnvelope.ciphertext());
            }
            for (ClarificationStore.Record challenge : challenges) {
                AgentStatePayloadCodec.Envelope challengeEnvelope = codec.encodeChallenge(
                        requestId, row.conversationId().toString(), challenge);
                applyDatabaseTimeout(deadline);
                jdbc.update("INSERT INTO " + clarificationTable + " (clarification_id, conversation_id, source_request_id, resume_token_hash, content_release_id, expires_at, consumed, payload_key_id, payload_nonce, payload_ciphertext) VALUES (?,?,?,?,?,?,false,?,?,?)",
                        challenge.challenge().getClarificationId(), row.conversationId(), requestId,
                        challenge.resumeTokenHash(), challenge.contentReleaseId(),
                        time(completedAt.plus(challengeTtl)), challengeEnvelope.keyId(),
                        challengeEnvelope.nonce(), challengeEnvelope.ciphertext());
            }
            applyDatabaseTimeout(deadline);
            int updated = jdbc.update("UPDATE " + table + " SET status='COMPLETED', settlement_key_id=?, settlement_nonce=?, settlement_ciphertext=?, updated_at=?, terminal_at=? WHERE request_id=? AND status='CLAIMED'",
                    envelope.keyId(), envelope.nonce(), envelope.ciphertext(),
                    time(completedAt), time(completedAt), requestId);
            if (updated != 1) status.setRollbackOnly();
            return updated == 1;
        }));
    }

    private void applyDatabaseTimeout(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long timeoutMillis = Math.min(
                databaseOperationTimeout.toMillis(), requireDatabaseTime(deadline));
        jdbc.execute("SET LOCAL statement_timeout = " + timeoutMillis);
    }

    private void applyStandaloneDatabaseTimeout() {
        jdbc.execute("SET LOCAL statement_timeout = " + databaseOperationTimeout.toMillis());
    }

    private void insertClaim(
            UUID requestId, String conversationId, byte[] fingerprint,
            Instant now, Duration leaseDuration,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        applyDatabaseTimeout(deadline);
        jdbc.update("INSERT INTO " + table + " (request_id, conversation_id,"
                        + " request_fingerprint, fingerprint_key_id, status, lease_expires_at,"
                        + " created_at, updated_at, absolute_expires_at) VALUES (?,?,?,?,?,?,?,?,?)",
                requestId, UUID.fromString(conversationId), fingerprint,
                currentFingerprintKeyId, "CLAIMED",
                time(now.plus(leaseDuration)), time(now), time(now), time(now.plus(absoluteTtl)));
    }

    private void rotateReplaySession(
            String conversationId,
            ConversationSessionStore.Session tentativeSession,
            Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        applyDatabaseTimeout(deadline);
        List<ReplayChallengeRow> live = jdbc.query(
                "SELECT clarification_id, source_request_id, content_release_id, "
                        + "payload_key_id, payload_nonce, payload_ciphertext FROM "
                        + clarificationTable
                        + " WHERE conversation_id=? AND consumed=false AND expires_at>?"
                        + " ORDER BY clarification_id LIMIT 33 FOR UPDATE",
                (result, index) -> new ReplayChallengeRow(
                        result.getString("clarification_id"),
                        result.getObject("source_request_id", UUID.class),
                        result.getString("content_release_id"),
                        new AgentStatePayloadCodec.Envelope(
                                result.getString("payload_key_id"),
                                result.getBytes("payload_nonce"),
                                result.getBytes("payload_ciphertext"))),
                UUID.fromString(conversationId), time(now));
        if (live.size() > 32) {
            throw new IllegalStateException("live clarification rebind limit exceeded");
        }
        for (ReplayChallengeRow challenge : live) {
            ClarificationStore.Record current = codec.decodeChallenge(
                    challenge.sourceRequestId(), conversationId,
                    challenge.clarificationId(), challenge.envelope());
            ClarificationStore.Record rebound = new ClarificationStore.Record(
                    current.conversationId(), tentativeSession.tokenHash(),
                    current.contentReleaseId(), current.challenge(),
                    current.choiceBindings(), current.textBindings(), current.blockedGoal());
            AgentStatePayloadCodec.Envelope envelope = codec.encodeChallenge(
                    challenge.sourceRequestId(), conversationId, rebound);
            applyDatabaseTimeout(deadline);
            jdbc.update("UPDATE " + clarificationTable
                            + " SET resume_token_hash=?, payload_key_id=?, payload_nonce=?,"
                            + " payload_ciphertext=? WHERE clarification_id=?",
                    tentativeSession.tokenHash(), envelope.keyId(), envelope.nonce(),
                    envelope.ciphertext(), challenge.clarificationId());
        }
        java.util.Map<UUID, java.util.Set<String>> challengeIdsByRequest = live.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ReplayChallengeRow::sourceRequestId,
                        java.util.stream.Collectors.mapping(
                                ReplayChallengeRow::clarificationId,
                                java.util.stream.Collectors.toSet())));
        for (java.util.Map.Entry<UUID, java.util.Set<String>> entry
                : challengeIdsByRequest.entrySet()) {
            applyDatabaseTimeout(deadline);
            Row source = select(entry.getKey(), true).orElseThrow(() ->
                    new IllegalStateException("clarification source execution is missing"));
            AgentStatePayloadCodec.SettlementPayload payload = payload(source);
            List<ClarificationStore.Record> reboundChallenges = payload.challenges().stream()
                    .map(challenge -> entry.getValue().contains(
                            challenge.challenge().getClarificationId())
                            ? rebind(challenge, tentativeSession.tokenHash()) : challenge)
                    .toList();
            AgentStatePayloadCodec.Envelope settlement = codec.encode(
                    source.requestId(), conversationId,
                    new AgentStatePayloadCodec.SettlementPayload(
                            payload.publicTurn(), payload.contexts(), reboundChallenges));
            applyDatabaseTimeout(deadline);
            jdbc.update("UPDATE " + table
                            + " SET settlement_key_id=?, settlement_nonce=?,"
                            + " settlement_ciphertext=?, updated_at=? WHERE request_id=?",
                    settlement.keyId(), settlement.nonce(), settlement.ciphertext(),
                    time(now), source.requestId());
        }
        applyDatabaseTimeout(deadline);
        int rotated = jdbc.update("UPDATE " + sessionTable
                        + " SET resume_token_hash=?, token_key_id=?"
                        + " WHERE conversation_id=? AND revoked_at IS NULL"
                        + " AND absolute_expires_at>?",
                tentativeSession.tokenHash(), tokenKeyId,
                UUID.fromString(conversationId), time(now));
        if (rotated != 1) {
            throw new IllegalStateException("replay session rotation failed");
        }
    }

    private ClarificationStore.Record rebind(
            ClarificationStore.Record current, byte[] tokenHash) {
        return new ClarificationStore.Record(
                current.conversationId(), tokenHash, current.contentReleaseId(),
                current.challenge(), current.choiceBindings(), current.textBindings(),
                current.blockedGoal());
    }

    private long requireDatabaseTime(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long remainingMillis = deadline.remainingMillis();
        if (remainingMillis < 1) {
            throw new IllegalStateException("agent state deadline exceeded");
        }
        return remainingMillis;
    }

    private boolean authorizeClaimSession(
            String conversationId, SessionAccess access, Instant now) {
        if (!conversationId.equals(access.conversationId())) return false;
        SessionRow row = selectSession(conversationId, true).orElse(null);
        if (access.tentativeSession() != null) {
            if (row == null) return true;
            Instant createdAt = access.tentativeSession().createdAt();
            if (row.revokedAt() != null && now.isBefore(row.absoluteExpiresAt())) return false;
            return now.isBefore(row.absoluteExpiresAt())
                    || !createdAt.isBefore(row.absoluteExpiresAt())
                    && !MessageDigest.isEqual(
                    access.tentativeSession().tokenHash(), row.tokenHash());
        }
        return liveSession(row, access.tokenHash(), now);
    }

    private boolean authorizeSettlementSession(
            SessionAccess access, ConversationSessionStore.Session sessionToCreate,
            Instant now) {
        SessionRow row = selectSession(access.conversationId(), true).orElse(null);
        if (access.tentativeSession() != null) {
            if (sessionToCreate == null
                    || !sessionToCreate.conversationId().equals(access.conversationId())) return false;
            if (row == null) return true;
            if (row.revokedAt() != null && now.isBefore(row.absoluteExpiresAt())) return false;
            return now.isBefore(row.absoluteExpiresAt())
                    || !sessionToCreate.createdAt().isBefore(row.absoluteExpiresAt())
                    && !MessageDigest.isEqual(sessionToCreate.tokenHash(), row.tokenHash());
        }
        return sessionToCreate == null && liveSession(row, access.tokenHash(), now);
    }

    private boolean liveSession(SessionRow row, byte[] tokenHash, Instant now) {
        return row != null && row.revokedAt() == null
                && now.isBefore(row.absoluteExpiresAt())
                && supportedTokenKeyIds.contains(row.tokenKeyId())
                && MessageDigest.isEqual(row.tokenHash(), tokenHash);
    }

    @Override public boolean cancel(UUID requestId, String conversationId, Instant cancelledAt) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            applyStandaloneDatabaseTimeout();
            Row row = select(requestId, true).orElse(null);
            if (row == null || !row.conversationId().toString().equals(conversationId)
                    || row.status() != TurnExecutionRecord.Status.CLAIMED) return false;
            if (!cancelledAt.isBefore(row.absoluteExpiresAt())) {
                jdbc.update("DELETE FROM " + table + " WHERE request_id=?", requestId);
                return false;
            }
            return jdbc.update("UPDATE " + table + " SET status='CANCELLED', updated_at=?, terminal_at=? WHERE request_id=? AND status='CLAIMED'",
                    time(cancelledAt), time(cancelledAt), requestId) == 1;
        }));
    }

    @Override public Optional<TurnExecutionRecord> find(UUID requestId) {
        return transactions.execute(status -> {
            applyStandaloneDatabaseTimeout();
            return select(requestId, false)
                .filter(row -> clock.instant().isBefore(row.absoluteExpiresAt()))
                .map(row -> {
            AgentStatePayloadCodec.SettlementPayload payload =
                    row.status() == TurnExecutionRecord.Status.COMPLETED ? payload(row) : null;
            return TurnExecutionRecord.restore(
                    row.requestId(), row.conversationId().toString(), row.fingerprint(),
                    row.fingerprintKeyId(), row.status(),
                    row.leaseExpiresAt(), payload == null ? null : payload.publicTurn(),
                    payload == null ? List.of() : payload.contexts(),
                    payload == null ? List.of() : payload.challenges(), row.terminalAt());
                });
        });
    }

    @Override public boolean clearConversation(
            String conversationId, byte[] tokenHash, Instant clearedAt) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            applyStandaloneDatabaseTimeout();
            SessionRow session = selectSession(conversationId, true).orElse(null);
            if (!liveSession(session, tokenHash, clearedAt)) return false;
            jdbc.update("UPDATE " + sessionTable
                            + " SET revoked_at=? WHERE conversation_id=? AND revoked_at IS NULL",
                    time(clearedAt), UUID.fromString(conversationId));
            jdbc.update("DELETE FROM " + table + " WHERE conversation_id=?",
                    UUID.fromString(conversationId));
            return true;
        }));
    }

    /**
     * 在一个短事务中按全局 batch 预算清理短期状态。
     *
     * <p>计数只按固定类别汇总，不暴露 conversation、request 或 key id。
     * 外键本应阻止孤儿行；显式孤儿清理用于修复约束曾被禁用后的残留。</p>
     */
    public CleanupResult cleanup(Instant now) {
        java.util.Objects.requireNonNull(now, "now");
        assertKeyCoverage(now);
        return transactions.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout = " + databaseOperationTimeout.toMillis());
            CleanupAccumulator counts = new CleanupAccumulator(cleanupBatchSize);
            counts.expiredContexts = deleteBatch(
                    contextTable, "t.expires_at<=? OR EXISTS (SELECT 1 FROM " + table
                            + " e WHERE e.request_id=t.source_request_id AND e.absolute_expires_at<=?)",
                    counts.remaining(), time(now), time(now));
            counts.consume(counts.expiredContexts);
            counts.expiredChallenges = deleteBatch(
                    clarificationTable, "t.expires_at<=? OR EXISTS (SELECT 1 FROM " + table
                            + " e WHERE e.request_id=t.source_request_id AND e.absolute_expires_at<=?)",
                    counts.remaining(), time(now), time(now));
            counts.consume(counts.expiredChallenges);
            counts.expiredExecutions = deleteBatch(
                    table, "t.absolute_expires_at<=? AND NOT EXISTS (SELECT 1 FROM "
                            + contextTable + " c WHERE c.source_request_id=t.request_id)"
                            + " AND NOT EXISTS (SELECT 1 FROM " + clarificationTable
                            + " q WHERE q.source_request_id=t.request_id)",
                    counts.remaining(), time(now));
            counts.consume(counts.expiredExecutions);
            counts.revokedSessions = deleteBatch(
                    sessionTable, "t.revoked_at IS NOT NULL AND t.absolute_expires_at<=?",
                    counts.remaining(), time(now));
            counts.consume(counts.revokedSessions);
            counts.expiredSessions = deleteBatch(
                    sessionTable, "t.absolute_expires_at<=?", counts.remaining(), time(now));
            counts.consume(counts.expiredSessions);

            counts.orphanRows += deleteBatch(
                    contextTable,
                    "NOT EXISTS (SELECT 1 FROM " + table
                            + " e WHERE e.request_id=t.source_request_id)",
                    counts.remaining());
            counts.consume(counts.orphanRows);
            int orphanChallenges = deleteBatch(
                    clarificationTable,
                    "NOT EXISTS (SELECT 1 FROM " + table
                            + " e WHERE e.request_id=t.source_request_id)",
                    counts.remaining());
            counts.orphanRows += orphanChallenges;
            counts.consume(orphanChallenges);

            int unsupportedContexts = deleteUnsupported(
                    contextTable, "payload_key_id", codec.supportedKeyIds(), counts.remaining());
            counts.unsupportedKeys += unsupportedContexts;
            counts.consume(unsupportedContexts);
            int unsupportedChallenges = deleteUnsupported(
                    clarificationTable, "payload_key_id", codec.supportedKeyIds(), counts.remaining());
            counts.unsupportedKeys += unsupportedChallenges;
            counts.consume(unsupportedChallenges);
            int unsupportedContextParents = deleteChildrenOfUnsupportedExecution(
                    contextTable, codec.supportedKeyIds(), counts.remaining());
            counts.unsupportedKeys += unsupportedContextParents;
            counts.consume(unsupportedContextParents);
            int unsupportedChallengeParents = deleteChildrenOfUnsupportedExecution(
                    clarificationTable, codec.supportedKeyIds(), counts.remaining());
            counts.unsupportedKeys += unsupportedChallengeParents;
            counts.consume(unsupportedChallengeParents);
            String unsupportedExecution = unsupportedCondition(
                    "t.settlement_key_id", codec.supportedKeyIds())
                    + " AND NOT EXISTS (SELECT 1 FROM " + contextTable
                    + " c WHERE c.source_request_id=t.request_id)"
                    + " AND NOT EXISTS (SELECT 1 FROM " + clarificationTable
                    + " q WHERE q.source_request_id=t.request_id)";
            int unsupportedExecutions = deleteBatch(
                    table, unsupportedExecution, counts.remaining(),
                    codec.supportedKeyIds().toArray());
            counts.unsupportedKeys += unsupportedExecutions;
            counts.consume(unsupportedExecutions);
            int unsupportedSessions = deleteUnsupported(
                    sessionTable, "token_key_id", supportedTokenKeyIds, counts.remaining());
            counts.unsupportedKeys += unsupportedSessions;
            counts.consume(unsupportedSessions);
            return counts.result();
        });
    }

    public CleanupResult cleanup() {
        return cleanup(clock.instant());
    }

    public void assertKeyCoverage(Instant now) {
        java.util.Objects.requireNonNull(now, "now");
        transactions.executeWithoutResult(status -> {
            applyStandaloneDatabaseTimeout();
            java.util.LinkedHashSet<String> payloadKeyIds = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> fingerprintKeyIds = new java.util.LinkedHashSet<>(
                    jdbc.queryForList("SELECT DISTINCT fingerprint_key_id FROM " + table
                                    + " WHERE absolute_expires_at>?",
                            String.class, time(now)));
            payloadKeyIds.addAll(jdbc.queryForList(
                    "SELECT DISTINCT settlement_key_id FROM " + table
                            + " WHERE absolute_expires_at>? AND settlement_key_id IS NOT NULL",
                    String.class, time(now)));
            payloadKeyIds.addAll(jdbc.queryForList(
                    "SELECT DISTINCT payload_key_id FROM " + contextTable
                            + " WHERE expires_at>?", String.class, time(now)));
            payloadKeyIds.addAll(jdbc.queryForList(
                    "SELECT DISTINCT payload_key_id FROM " + clarificationTable
                            + " WHERE expires_at>?", String.class, time(now)));
            java.util.LinkedHashSet<String> tokenKeyIds = new java.util.LinkedHashSet<>(
                    jdbc.queryForList("SELECT DISTINCT token_key_id FROM " + sessionTable
                                    + " WHERE absolute_expires_at>?",
                            String.class, time(now)));
            if (!supportedFingerprintKeyIds.containsAll(fingerprintKeyIds)
                    || !codec.supportedKeyIds().containsAll(payloadKeyIds)
                    || !supportedTokenKeyIds.containsAll(tokenKeyIds)) {
                throw new IllegalStateException(
                        "unexpired Agent State requires an unavailable key");
            }
        });
    }

    private int deleteUnsupported(
            String targetTable, String keyColumn,
            Set<String> supportedKeys, int limit) {
        if (limit < 1) return 0;
        String condition = unsupportedCondition("t." + keyColumn, supportedKeys);
        return deleteBatch(targetTable, condition, limit, supportedKeys.toArray());
    }

    private int deleteChildrenOfUnsupportedExecution(
            String childTable, Set<String> supportedKeys, int limit) {
        if (limit < 1) return 0;
        String condition = "EXISTS (SELECT 1 FROM " + table
                + " e WHERE e.request_id=t.source_request_id AND "
                + unsupportedCondition("e.settlement_key_id", supportedKeys) + ")";
        return deleteBatch(childTable, condition, limit, supportedKeys.toArray());
    }

    private String unsupportedCondition(String expression, Set<String> supportedKeys) {
        return expression + " IS NOT NULL AND " + expression + " NOT IN ("
                + String.join(",", java.util.Collections.nCopies(
                supportedKeys.size(), "?")) + ")";
    }

    private int deleteBatch(
            String targetTable, String condition, int limit, Object... parameters) {
        if (limit < 1) return 0;
        Object[] arguments = java.util.Arrays.copyOf(parameters, parameters.length + 1);
        arguments[parameters.length] = limit;
        return jdbc.update("WITH doomed AS (SELECT t.ctid FROM " + targetTable
                        + " t WHERE " + condition + " ORDER BY t.ctid LIMIT ?) DELETE FROM "
                        + targetTable + " t USING doomed WHERE t.ctid=doomed.ctid",
                arguments);
    }

    @Override public Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
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
        });
    }

    @Override public ClarificationStore.ConsumeResult consumeClarification(
            String clarificationId, String conversationId, byte[] tokenHash,
            String currentReleaseId, ClarificationStore.ClarificationAnswer answer,
            Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
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
                applyDatabaseTimeout(deadline);
                jdbc.update("UPDATE " + clarificationTable
                        + " SET consumed=true WHERE clarification_id=?", clarificationId);
            }
            return consumed;
        });
    }

    private Optional<Row> select(UUID requestId, boolean lock) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT request_id, conversation_id, request_fingerprint,"
                            + " fingerprint_key_id, status, lease_expires_at, settlement_key_id,"
                            + " settlement_nonce, settlement_ciphertext, terminal_at, absolute_expires_at FROM "
                            + table + " WHERE request_id=?" + (lock ? " FOR UPDATE" : ""),
                    (result, index) -> row(result), requestId));
        } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }
    private Optional<SessionRow> selectSession(String conversationId, boolean lock) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT resume_token_hash, token_key_id, absolute_expires_at, revoked_at FROM "
                            + sessionTable + " WHERE conversation_id=?"
                            + (lock ? " FOR UPDATE" : ""),
                    (result, index) -> {
                        OffsetDateTime revoked = result.getObject(
                                "revoked_at", OffsetDateTime.class);
                        return new SessionRow(
                                result.getBytes("resume_token_hash"),
                                result.getString("token_key_id"),
                                result.getObject("absolute_expires_at", OffsetDateTime.class).toInstant(),
                                revoked == null ? null : revoked.toInstant());
                    }, UUID.fromString(conversationId)));
        } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
    }
    private Row row(ResultSet result) throws SQLException {
        OffsetDateTime terminal = result.getObject("terminal_at", OffsetDateTime.class);
        return new Row(
                result.getObject("request_id", UUID.class),
                result.getObject("conversation_id", UUID.class),
                result.getBytes("request_fingerprint"),
                result.getString("fingerprint_key_id"),
                TurnExecutionRecord.Status.valueOf(result.getString("status")),
                result.getObject("lease_expires_at", OffsetDateTime.class).toInstant(),
                result.getString("settlement_key_id"), result.getBytes("settlement_nonce"),
                result.getBytes("settlement_ciphertext"), terminal == null ? null : terminal.toInstant(),
                result.getObject("absolute_expires_at", OffsetDateTime.class).toInstant());
    }
    private AgentStatePayloadCodec.SettlementPayload payload(Row row) {
        return codec.decode(row.requestId(), row.conversationId().toString(),
                new AgentStatePayloadCodec.Envelope(
                        row.keyId(), row.nonce(), row.ciphertext()));
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
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
            String fingerprintKeyId, TurnExecutionRecord.Status status, Instant leaseExpiresAt,
            String keyId, byte[] nonce, byte[] ciphertext, Instant terminalAt,
            Instant absoluteExpiresAt) { }
    private record ChallengeRow(
            UUID sourceRequestId, byte[] tokenHash, String contentReleaseId,
            Instant expiresAt, boolean consumed, AgentStatePayloadCodec.Envelope envelope) { }
    private record SessionRow(
            byte[] tokenHash, String tokenKeyId,
            Instant absoluteExpiresAt, Instant revokedAt) { }
    private record ReplayChallengeRow(
            String clarificationId, UUID sourceRequestId, String contentReleaseId,
            AgentStatePayloadCodec.Envelope envelope) { }

    public record CleanupResult(
            int expiredExecutions, int expiredContexts, int expiredChallenges,
            int expiredSessions, int revokedSessions, int orphanRows,
            int unsupportedKeys) {
        public int total() {
            return expiredExecutions + expiredContexts + expiredChallenges
                    + expiredSessions + revokedSessions + orphanRows + unsupportedKeys;
        }
    }

    private static final class CleanupAccumulator {
        private int remaining;
        private int expiredExecutions;
        private int expiredContexts;
        private int expiredChallenges;
        private int expiredSessions;
        private int revokedSessions;
        private int orphanRows;
        private int unsupportedKeys;

        private CleanupAccumulator(int limit) { remaining = limit; }
        private int remaining() { return remaining; }
        private void consume(int count) { remaining = Math.max(0, remaining - count); }
        private CleanupResult result() {
            return new CleanupResult(
                    expiredExecutions, expiredContexts, expiredChallenges,
                    expiredSessions, revokedSessions, orphanRows, unsupportedKeys);
        }
    }
}
