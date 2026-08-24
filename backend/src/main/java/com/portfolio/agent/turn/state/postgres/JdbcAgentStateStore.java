package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
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
    private final JdbcConversationSessionWriter sessionWriter;

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
        this.sessionWriter = new JdbcConversationSessionWriter(
                jdbc, sessionTable, tokenKeyId);
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
                return ClaimResult.replay(
                        payload(row).publicTurn(),
                        selectSession(conversationId, false)
                                .map(value -> sessionSnapshot(
                                        conversationId, value))
                                .orElse(null));
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
        return complete(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                com.portfolio.agent.turn.continuation.DiscussionStateMutation.none());
    }

    @Override public boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation) {
        return complete(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation,
                com.portfolio.agent.turn.continuation.ClarificationSettlementMutation.none());
    }

    @Override public boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation) {
        return settleWithSession(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation, clarificationMutation, null).completed();
    }

    @Override public SettlementResult completeWithSession(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation) {
        return settleWithSession(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation, clarificationMutation, null);
    }

    @Override public SettlementResult completeWithSession(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation,
            ConversationSemanticState semanticState) {
        return settleWithSession(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation, clarificationMutation, semanticState);
    }

    private SettlementResult settleWithSession(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation,
            ConversationSemanticState semanticState) {
        requireDatabaseTime(deadline);
        SettlementResult result = transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            if (!authorizeSettlementSession(
                    sessionAccess, sessionToCreate, completedAt)) {
                return new SettlementResult(false, null);
            }
            Row row = select(requestId, true).orElse(null);
            if (row == null || row.status() != TurnExecutionRecord.Status.CLAIMED
                    || !MessageDigest.isEqual(row.fingerprint(), fingerprint)
                    || !completedAt.isBefore(row.absoluteExpiresAt())) {
                return new SettlementResult(false, null);
            }
            if (!applyClarificationSettlement(
                    requestId, row.conversationId().toString(),
                    clarificationMutation, completedAt, deadline)) {
                status.setRollbackOnly();
                return new SettlementResult(false, null);
            }
            AgentStatePayloadCodec.Envelope envelope = codec.encode(
                    requestId, row.conversationId().toString(),
                    new AgentStatePayloadCodec.SettlementPayload(
                            snapshot, contexts, challenges));
            if (sessionToCreate != null) {
                applyDatabaseTimeout(deadline);
                sessionWriter.upsert(sessionToCreate);
            }
            if (!applyDiscussionMutation(
                    row.conversationId(), discussionMutation, deadline)) {
                status.setRollbackOnly();
                return new SettlementResult(false, null);
            }
            if (semanticState != null) {
                AgentStatePayloadCodec.Envelope semanticEnvelope =
                        codec.encodeSemanticState(
                                row.conversationId().toString(), semanticState);
                applyDatabaseTimeout(deadline);
                if (jdbc.update("UPDATE " + sessionTable
                                + " SET semantic_state_key_id=?,"
                                + " semantic_state_nonce=?,"
                                + " semantic_state_ciphertext=?,"
                                + " semantic_state_updated_at=?"
                                + " WHERE conversation_id=?",
                        semanticEnvelope.keyId(), semanticEnvelope.nonce(),
                        semanticEnvelope.ciphertext(), time(semanticState.updatedAt()),
                        row.conversationId()) != 1) {
                    status.setRollbackOnly();
                    return new SettlementResult(false, null);
                }
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
            if (updated != 1) {
                status.setRollbackOnly();
                return new SettlementResult(false, null);
            }
            ConversationSessionStore.Session current =
                    selectSession(sessionAccess.conversationId(), false)
                            .map(value -> sessionSnapshot(
                                    sessionAccess.conversationId(), value))
                            .orElse(null);
            return new SettlementResult(true, current);
        });
        return result == null ? new SettlementResult(false, null) : result;
    }

    private boolean applyDiscussionMutation(
            UUID conversationId,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation mutation,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (mutation.isNone()) return true;
        applyDatabaseTimeout(deadline);
        String current = jdbc.queryForObject(
                "SELECT active_discussion_handle FROM " + sessionTable
                        + " WHERE conversation_id=? FOR UPDATE",
                String.class, conversationId);
        String expected = mutation.getExpectedGeneration().orElse(null);
        if (expected == null ? current != null : !expected.equals(current)) {
            return false;
        }
        return switch (mutation.getKind()) {
            case NONE, GUARD -> true;
            case CLEAR -> jdbc.update(
                    "UPDATE " + sessionTable
                            + " SET active_discussion_handle=NULL,"
                            + " active_discussion_project_id=NULL,"
                            + " active_discussion_expires_at=NULL,"
                            + " revision=revision+1 WHERE conversation_id=?",
                    conversationId) == 1;
            case REPLACE -> {
                com.portfolio.agent.turn.continuation.ActiveDiscussionPointer replacement =
                        mutation.getReplacement().orElseThrow();
                yield jdbc.update(
                        "UPDATE " + sessionTable
                                + " SET active_discussion_handle=?,"
                                + " active_discussion_project_id=?,"
                                + " active_discussion_expires_at=?,"
                                + " revision=revision+1 WHERE conversation_id=?",
                        replacement.getContextHandle(),
                        replacement.getProjectId(),
                        time(replacement.getContextExpiresAt()),
                        conversationId) == 1;
            }
        };
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
                    current.choiceBindings(), current.textBindings(), current.resumeTemplate());
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
                current.resumeTemplate());
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
            boolean cancelled = jdbc.update("UPDATE " + table + " SET status='CANCELLED', updated_at=?, terminal_at=? WHERE request_id=? AND status='CLAIMED'",
                    time(cancelledAt), time(cancelledAt), requestId) == 1;
            if (cancelled) {
                applyStandaloneDatabaseTimeout();
                jdbc.update("UPDATE " + clarificationTable
                                + " SET reserved_by_request_id=NULL,"
                                + " reservation_expires_at=NULL"
                                + " WHERE consumed=false"
                                + " AND reserved_by_request_id=?",
                        requestId);
            }
            return cancelled;
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
            int unsupportedSemanticStates = clearUnsupportedSemanticStates(
                    codec.supportedKeyIds(), counts.remaining());
            counts.unsupportedKeys += unsupportedSemanticStates;
            counts.consume(unsupportedSemanticStates);
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
            payloadKeyIds.addAll(jdbc.queryForList(
                    "SELECT DISTINCT semantic_state_key_id FROM " + sessionTable
                            + " WHERE absolute_expires_at>?"
                            + " AND semantic_state_key_id IS NOT NULL",
                    String.class, time(now)));
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

    private int clearUnsupportedSemanticStates(
            Set<String> supportedKeys, int limit) {
        if (limit < 1) return 0;
        Object[] arguments = java.util.Arrays.copyOf(
                supportedKeys.toArray(), supportedKeys.size() + 1);
        arguments[supportedKeys.size()] = limit;
        return jdbc.update("WITH doomed AS (SELECT t.ctid FROM " + sessionTable
                        + " t WHERE " + unsupportedCondition(
                        "t.semantic_state_key_id", supportedKeys)
                        + " LIMIT ?) UPDATE " + sessionTable + " t SET"
                        + " semantic_state_key_id=NULL, semantic_state_nonce=NULL,"
                        + " semantic_state_ciphertext=NULL,"
                        + " semantic_state_updated_at=NULL FROM doomed"
                        + " WHERE t.ctid=doomed.ctid",
                arguments);
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

    @Override public ClarificationStore.ReserveResult reserveClarification(
            String clarificationId, String conversationId, byte[] tokenHash,
            String currentReleaseId, ClarificationStore.ClarificationAnswer answer,
            UUID requestId, Instant reservationExpiresAt, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            ChallengeRow row;
            try {
                row = jdbc.queryForObject(
                        "SELECT source_request_id, resume_token_hash, content_release_id, expires_at, consumed, reserved_by_request_id, reservation_expires_at, payload_key_id, payload_nonce, payload_ciphertext FROM "
                                + clarificationTable + " WHERE clarification_id=? FOR UPDATE",
                        (result, index) -> challengeRow(result), clarificationId);
            } catch (EmptyResultDataAccessException missing) {
                return ClarificationStore.ReserveResult.of(ClarificationStore.Status.NOT_FOUND);
            }
            if (row.consumed()) return ClarificationStore.ReserveResult.of(ClarificationStore.Status.ALREADY_CONSUMED);
            if (!now.isBefore(row.expiresAt())) return ClarificationStore.ReserveResult.of(ClarificationStore.Status.EXPIRED);
            if (!MessageDigest.isEqual(row.tokenHash(), tokenHash)) {
                return ClarificationStore.ReserveResult.of(ClarificationStore.Status.UNAUTHORIZED);
            }
            if (!row.contentReleaseId().equals(currentReleaseId)) {
                return ClarificationStore.ReserveResult.of(ClarificationStore.Status.STALE_RELEASE);
            }
            if (row.reservedByRequestId() != null
                    && !row.reservedByRequestId().equals(requestId)
                    && now.isBefore(row.reservationExpiresAt())) {
                return ClarificationStore.ReserveResult.inProgress(
                        Math.max(1L, Duration.between(
                                now, row.reservationExpiresAt()).toSeconds()));
            }
            ClarificationStore.Record record = codec.decodeChallenge(
                    row.sourceRequestId(), conversationId, clarificationId, row.envelope());
            ClarificationStore validator = new ClarificationStore(
                    java.time.Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(1));
            validator.save(record);
            Instant boundedReservationExpiry = earlier(
                    reservationExpiresAt, row.expiresAt());
            ClarificationStore.ReserveResult reserved = validator.reserve(
                    clarificationId, conversationId, tokenHash,
                    currentReleaseId, answer, requestId,
                    boundedReservationExpiry);
            if (reserved.status() == ClarificationStore.Status.RESERVED) {
                applyDatabaseTimeout(deadline);
                int changed = jdbc.update("UPDATE " + clarificationTable
                                + " SET reserved_by_request_id=?, reservation_expires_at=?"
                                + " WHERE clarification_id=? AND consumed=false"
                                + " AND (reserved_by_request_id IS NULL"
                                + " OR reserved_by_request_id=?"
                                + " OR reservation_expires_at<=?)",
                        requestId, time(boundedReservationExpiry),
                        clarificationId, requestId, time(now));
                if (changed != 1) {
                    throw new IllegalStateException(
                            "clarification reservation lost its row lock");
                }
            }
            return reserved;
        });
    }

    private boolean applyClarificationSettlement(
            UUID requestId, String conversationId,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation mutation,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (mutation.isNone()) return true;
        applyDatabaseTimeout(deadline);
        ChallengeRow row;
        try {
            row = jdbc.queryForObject(
                    "SELECT source_request_id, resume_token_hash, content_release_id, expires_at, consumed, reserved_by_request_id, reservation_expires_at, payload_key_id, payload_nonce, payload_ciphertext FROM "
                            + clarificationTable
                            + " WHERE clarification_id=? FOR UPDATE",
                    (result, index) -> challengeRow(result),
                    mutation.clarificationId());
        } catch (EmptyResultDataAccessException missing) {
            return false;
        }
        if (row.consumed()
                || row.reservedByRequestId() == null
                || !row.reservedByRequestId().equals(requestId)
                || row.reservationExpiresAt() == null
                || !completedAt.isBefore(row.reservationExpiresAt())) {
            return false;
        }
        ClarificationStore.Record record = codec.decodeChallenge(
                row.sourceRequestId(), conversationId,
                mutation.clarificationId(), row.envelope());
        ClarificationStore validator = new ClarificationStore(
                java.time.Clock.fixed(completedAt, ZoneOffset.UTC),
                Duration.ofMinutes(1));
        validator.save(record);
        ClarificationStore.ReserveResult validation = validator.reserve(
                mutation.clarificationId(), conversationId,
                row.tokenHash(), row.contentReleaseId(), mutation.answer(),
                requestId, row.reservationExpiresAt());
        if (validation.status() != ClarificationStore.Status.RESERVED) {
            return false;
        }
        applyDatabaseTimeout(deadline);
        return jdbc.update("UPDATE " + clarificationTable
                        + " SET consumed=true, reserved_by_request_id=NULL,"
                        + " reservation_expires_at=NULL"
                        + " WHERE clarification_id=? AND consumed=false"
                        + " AND reserved_by_request_id=?",
                mutation.clarificationId(), requestId) == 1;
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
                    "SELECT resume_token_hash, token_key_id, created_at,"
                            + " absolute_expires_at, revoked_at,"
                            + " active_discussion_handle,"
                            + " active_discussion_project_id,"
                            + " active_discussion_expires_at, revision,"
                            + " semantic_state_key_id, semantic_state_nonce,"
                            + " semantic_state_ciphertext FROM "
                            + sessionTable + " WHERE conversation_id=?"
                            + (lock ? " FOR UPDATE" : ""),
                    (result, index) -> {
                        OffsetDateTime revoked = result.getObject(
                                "revoked_at", OffsetDateTime.class);
                        OffsetDateTime discussionExpiry = result.getObject(
                                "active_discussion_expires_at",
                                OffsetDateTime.class);
                        com.portfolio.agent.turn.continuation.ActiveDiscussionPointer pointer =
                                discussionExpiry == null ? null
                                        : new com.portfolio.agent.turn.continuation.ActiveDiscussionPointer(
                                        result.getString("active_discussion_handle"),
                                        result.getString("active_discussion_project_id"),
                                        discussionExpiry.toInstant());
                        String semanticKeyId = result.getString(
                                "semantic_state_key_id");
                        AgentStatePayloadCodec.Envelope semanticEnvelope =
                                semanticKeyId == null ? null
                                        : new AgentStatePayloadCodec.Envelope(
                                        semanticKeyId,
                                        result.getBytes("semantic_state_nonce"),
                                        result.getBytes("semantic_state_ciphertext"));
                        return new SessionRow(
                                result.getBytes("resume_token_hash"),
                                result.getString("token_key_id"),
                                result.getObject(
                                        "created_at", OffsetDateTime.class)
                                        .toInstant(),
                                result.getObject("absolute_expires_at", OffsetDateTime.class).toInstant(),
                                revoked == null ? null : revoked.toInstant(),
                                pointer, result.getLong("revision"),
                                semanticEnvelope);
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
    private ConversationSessionStore.Session sessionSnapshot(
            String conversationId, SessionRow row) {
        return new ConversationSessionStore.Session(
                conversationId, row.tokenHash(), row.createdAt(),
                row.absoluteExpiresAt(), row.activeDiscussionPointer(),
                row.discussionRevision(), row.semanticStateEnvelope() == null
                ? null : codec.decodeSemanticState(
                conversationId, row.semanticStateEnvelope()));
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
    private ChallengeRow challengeRow(ResultSet result) throws SQLException {
        OffsetDateTime reservationExpiry = result.getObject(
                "reservation_expires_at", OffsetDateTime.class);
        return new ChallengeRow(
                result.getObject("source_request_id", UUID.class),
                result.getBytes("resume_token_hash"), result.getString("content_release_id"),
                result.getObject("expires_at", OffsetDateTime.class).toInstant(),
                result.getBoolean("consumed"),
                result.getObject("reserved_by_request_id", UUID.class),
                reservationExpiry == null ? null : reservationExpiry.toInstant(),
                new AgentStatePayloadCodec.Envelope(
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
            Instant expiresAt, boolean consumed,
            UUID reservedByRequestId, Instant reservationExpiresAt,
            AgentStatePayloadCodec.Envelope envelope) { }
    private record SessionRow(
            byte[] tokenHash, String tokenKeyId,
            Instant createdAt, Instant absoluteExpiresAt,
            Instant revokedAt,
            com.portfolio.agent.turn.continuation.ActiveDiscussionPointer activeDiscussionPointer,
            long discussionRevision,
            AgentStatePayloadCodec.Envelope semanticStateEnvelope) { }
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
