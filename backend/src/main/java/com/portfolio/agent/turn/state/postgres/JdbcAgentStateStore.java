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

/**
 * PostgreSQL Agent State 权威：Claim/终态/重放的原子迁移与加密读写。
 *
 * <p>结算是一个事务内的单行加密更新：会话校验 → 行级锁复核（CLAIMED + 指纹）→
 * 澄清消费 → 结算密文 → 会话/讨论/语义状态 → 上下文与 challenge 行 → 终态迁移，
 * 任一步失败整体回滚。所有载荷经 {@link AgentStatePayloadCodec} 认证加密；每条
 * 语句都设置局部 statement_timeout 并与 TurnDeadline 取小。只保存加密短生命周期
 * typed state 与 persistence-safe 回放体，从不保存访客问题或原始模型输出。</p>
 */
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

    /**
     * 认领或重放一个 Turn（事务 + 行级锁）：绝对过期行先删除；无行则插入 CLAIMED
     * 并获得租期；已完成且指纹在轮换窗口内匹配的请求解密结算密文返回 REPLAY
     * （试探会话触发活跃 challenge 换绑与会话轮换）；租约未过期返回 IN_PROGRESS；
     * 租约过期重新认领；会话或指纹不匹配返回 CONFLICT/CANCELLED。
     */
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

    /**
     * 结算核心（单一事务）：会话校验 → 行级锁复核（CLAIMED + 指纹恒定时间相等 +
     * 未绝对过期）→ 澄清预留消费校验 → 各载荷加密写入（结算密文、会话、讨论
     * 变更、语义状态、上下文行、challenge 行）→ 单行终态迁移。任一步失败
     * setRollbackOnly 并返回未完成结果。
     */
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

    /**
     * 应用讨论状态变更（乐观并发）：FOR UPDATE 读取当前讨论 handle，必须与变更
     * 携带的期望世代一致（GUARD 只校验不改写；CLEAR 置空并递增修订；REPLACE
     * 写入新指针并递增修订）。
     */
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

    /** 设置局部 statement_timeout：数据库预算与 Turn 剩余时间取小。 */
    private void applyDatabaseTimeout(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long timeoutMillis = Math.min(
                databaseOperationTimeout.toMillis(), requireDatabaseTime(deadline));
        jdbc.execute("SET LOCAL statement_timeout = " + timeoutMillis);
    }

    /** 无 deadline 写路径的固定预算超时设置。 */
    private void applyStandaloneDatabaseTimeout() {
        jdbc.execute("SET LOCAL statement_timeout = " + databaseOperationTimeout.toMillis());
    }

    /** 插入一行新的 CLAIMED 记录（当前指纹 + 租期 + 绝对过期）。 */
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

    /**
     * 重放时的会话轮换：把该会话全部存活 challenge（上限 32，超出抛错）的
     * ResumeToken 哈希换绑到新试探会话，同步改写各源结算密文中的 challenge 副本，
     * 最后原子替换会话行的令牌哈希。任一步失败抛错回滚，避免新旧凭证同时可用。
     */
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

    /** 把 challenge 副本的令牌哈希替换为新哈希（会话轮换时重加密）。 */
    private ClarificationStore.Record rebind(
            ClarificationStore.Record current, byte[] tokenHash) {
        return new ClarificationStore.Record(
                current.conversationId(), tokenHash, current.contentReleaseId(),
                current.challenge(), current.choiceBindings(), current.textBindings(),
                current.resumeTemplate());
    }

    /**
     * 断言 Turn 仍有剩余时间。
     *
     * @throws IllegalStateException deadline 已过期
     */
    private long requireDatabaseTime(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long remainingMillis = deadline.remainingMillis();
        if (remainingMillis < 1) {
            throw new IllegalStateException("agent state deadline exceeded");
        }
        return remainingMillis;
    }

    /**
     * Claim 期会话校验：试探性会话在无既有会话时放行（首次匿名请求），在旧行
     * 绝对过期且哈希不同（换代）时也放行；已认证会话必须命中未吊销、未过期、
     * 密钥受支持且哈希恒定时间相等的行。
     */
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

    /** 结算期会话校验：与 Claim 期同规则，但试探性会话必须同时提交待建会话。 */
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

    /** 存活会话判定：未吊销、未绝对过期、密钥受支持且令牌哈希恒定时间相等。 */
    private boolean liveSession(SessionRow row, byte[] tokenHash, Instant now) {
        return row != null && row.revokedAt() == null
                && now.isBefore(row.absoluteExpiresAt())
                && supportedTokenKeyIds.contains(row.tokenKeyId())
                && MessageDigest.isEqual(row.tokenHash(), tokenHash);
    }

    /**
     * 取消 Turn（事务）：仅 CLAIMED 可迁移为 CANCELLED；绝对过期行直接删除并
     * 返回未取消；取消成功时释放该请求持有的澄清预留。
     */
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

    /** 按 requestId 读取未过期记录；COMPLETED 行解密结算密文重建完整记录。 */
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

    /** 凭存活会话凭证吊销会话并删除该会话全部 Turn 行（匿名清空）。 */
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

    /** 按当前时间执行一轮批量清理。 */
    public CleanupResult cleanup() {
        return cleanup(clock.instant());
    }

    /**
     * 密钥覆盖硬门：断言全部未过期数据的密钥（指纹/载荷/令牌）仍在支持集内。
     *
     * @throws IllegalStateException 存在未过期数据依赖已下线密钥（fail-closed 启动失败）
     */
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

    /** 删除密钥 id 不在支持集内的行（密钥下线后的强制清理）。 */
    private int deleteUnsupported(
            String targetTable, String keyColumn,
            Set<String> supportedKeys, int limit) {
        if (limit < 1) return 0;
        String condition = unsupportedCondition("t." + keyColumn, supportedKeys);
        return deleteBatch(targetTable, condition, limit, supportedKeys.toArray());
    }

    /** 把语义状态密钥不受支持的会话行的语义状态列置空（行本身保留）。 */
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

    /** 删除其源执行结算密钥不受支持的子行（上下文/challenge）。 */
    private int deleteChildrenOfUnsupportedExecution(
            String childTable, Set<String> supportedKeys, int limit) {
        if (limit < 1) return 0;
        String condition = "EXISTS (SELECT 1 FROM " + table
                + " e WHERE e.request_id=t.source_request_id AND "
                + unsupportedCondition("e.settlement_key_id", supportedKeys) + ")";
        return deleteBatch(childTable, condition, limit, supportedKeys.toArray());
    }

    /** 生成 "表达式非空且不在支持集内" 的 SQL 条件片段。 */
    private String unsupportedCondition(String expression, Set<String> supportedKeys) {
        return expression + " IS NOT NULL AND " + expression + " NOT IN ("
                + String.join(",", java.util.Collections.nCopies(
                supportedKeys.size(), "?")) + ")";
    }

    /** 有界批量删除：ctid 子查询限定 LIMIT，避免大表上无界 DELETE。 */
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

    /** 按 conversationId + contextHandle 解密读取未过期 ContinuationContext。 */
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

    /**
     * 澄清预留（FOR UPDATE 行锁）：按 不存在/已消费/过期/凭证不符/内容版本过期/
     * 他请求预留中 分类返回；校验通过时在内存 validator 上复验答案后写入预留
     * 标记（条件更新丢失行锁即抛错）。
     */
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

    /**
     * 结算时消费澄清预留：行锁下复核 未消费 + 本请求持有预留 + 预留未过期，
     * 解密后经内存 validator 复验答案，最后条件更新为已消费；任一条件不满足
     * 返回 false 使整个结算回滚。
     */
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

    /** 读取 Turn 执行行（可选 FOR UPDATE 行锁）。 */
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
    /** 读取会话行（含讨论指针与语义状态密文，可选 FOR UPDATE 行锁）。 */
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
    /** 执行行 → Row 的 ResultSet 映射。 */
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
    /** 解密执行行的结算密文。 */
    private AgentStatePayloadCodec.SettlementPayload payload(Row row) {
        return codec.decode(row.requestId(), row.conversationId().toString(),
                new AgentStatePayloadCodec.Envelope(
                        row.keyId(), row.nonce(), row.ciphertext()));
    }
    /** 会话行 → 对外 Session 快照（含语义状态解密）。 */
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
    /** challenge 行的 ResultSet 映射（含预留状态与密文信封）。 */
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
    /** Turn 执行行的内部快照（含结算密文信封）。 */
    private record Row(
            UUID requestId, UUID conversationId, byte[] fingerprint,
            String fingerprintKeyId, TurnExecutionRecord.Status status, Instant leaseExpiresAt,
            String keyId, byte[] nonce, byte[] ciphertext, Instant terminalAt,
            Instant absoluteExpiresAt) { }
    /** challenge 行的内部快照（含预留归属与密文信封）。 */
    private record ChallengeRow(
            UUID sourceRequestId, byte[] tokenHash, String contentReleaseId,
            Instant expiresAt, boolean consumed,
            UUID reservedByRequestId, Instant reservationExpiresAt,
            AgentStatePayloadCodec.Envelope envelope) { }
    /** 会话行的内部快照（含讨论指针与语义状态密文信封）。 */
    private record SessionRow(
            byte[] tokenHash, String tokenKeyId,
            Instant createdAt, Instant absoluteExpiresAt,
            Instant revokedAt,
            com.portfolio.agent.turn.continuation.ActiveDiscussionPointer activeDiscussionPointer,
            long discussionRevision,
            AgentStatePayloadCodec.Envelope semanticStateEnvelope) { }
    /** 会话轮换期间待换绑的 challenge 行（限 32 条）。 */
    private record ReplayChallengeRow(
            String clarificationId, UUID sourceRequestId, String contentReleaseId,
            AgentStatePayloadCodec.Envelope envelope) { }

    /** 一轮清理的固定类别计数（不含任何会话或密钥标识）。 */
    public record CleanupResult(
            int expiredExecutions, int expiredContexts, int expiredChallenges,
            int expiredSessions, int revokedSessions, int orphanRows,
            int unsupportedKeys) {
        public int total() {
            return expiredExecutions + expiredContexts + expiredChallenges
                    + expiredSessions + revokedSessions + orphanRows + unsupportedKeys;
        }
    }

    /** 清理批次预算累加器：七类计数共享一个全局 limit。 */
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
