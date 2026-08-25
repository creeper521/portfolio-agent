package com.portfolio.agent.turn.state.memory;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintSet;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;

/**
 * 进程内 Agent State（IN_MEMORY 模式）：与 JDBC 存储相同的单一终态闸门。
 *
 * <p>仅用于快速测试与定向诊断，不跨进程、重启即失。终态迁移、重放与澄清预留
 * 语义与 PostgreSQL 实现保持一致，保证生命周期服务在两种模式下行为等价。</p>
 */
public final class InMemoryTurnExecutionStore implements AgentStateStore {
    private final ConcurrentHashMap<UUID, TurnExecutionRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Instant> absoluteExpiries = new ConcurrentHashMap<>();
    private final ClarificationStore clarificationStore;
    private final Duration absoluteTtl;
    private final InMemoryConversationSessionStore sessionStore;
    private final Clock clock;

    public InMemoryTurnExecutionStore() {
        this(new ClarificationStore(
                java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(5)),
                Duration.ofMinutes(30), new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(ClarificationStore clarificationStore) {
        this(clarificationStore, Duration.ofMinutes(30),
                new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(
            ClarificationStore clarificationStore, Duration absoluteTtl) {
        this(clarificationStore, absoluteTtl,
                new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(
            ClarificationStore clarificationStore, Duration absoluteTtl,
            InMemoryConversationSessionStore sessionStore, Clock clock) {
        this.clarificationStore = clarificationStore;
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("absoluteTtl is invalid");
        }
        this.absoluteTtl = absoluteTtl;
        this.sessionStore = java.util.Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /**
     * 认领或重放一个 Turn：会话校验失败返回 CANCELLED；绝对过期记录视同不存在；
     * 已完成且指纹匹配的请求重放快照（试探性会话触发 challenge 重绑与新会话落库）；
     * 租约未过期返回 IN_PROGRESS，租约过期允许重新认领；指纹或会话不匹配返回
     * CONFLICT。
     */
    @Override public synchronized ClaimResult claim(
            UUID requestId, String conversationId, RequestFingerprintSet fingerprints,
            SessionAccess sessionAccess, Instant now, Duration leaseDuration,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) {
            throw new IllegalStateException("agent claim deadline exceeded");
        }
        if (!authorizeSession(conversationId, sessionAccess, now)) {
            return ClaimResult.state(ClaimResult.Status.CANCELLED);
        }
        AtomicReference<ClaimResult> result = new AtomicReference<>();
        records.compute(requestId, (key, existing) -> {
            Instant absoluteExpiry = absoluteExpiries.get(requestId);
            if (existing != null && absoluteExpiry != null && !now.isBefore(absoluteExpiry)) {
                absoluteExpiries.remove(requestId);
                existing = null;
            }
            if (existing == null) {
                result.set(ClaimResult.claimed());
                absoluteExpiries.put(requestId, now.plus(absoluteTtl));
                return TurnExecutionRecord.claimed(
                        requestId, conversationId, fingerprints.current(),
                        fingerprints.currentKeyId(), now.plus(leaseDuration));
            }
            if (!existing.getConversationId().equals(conversationId)
                    || !fingerprints.matches(existing.getRequestFingerprint())) {
                result.set(ClaimResult.state(ClaimResult.Status.CONFLICT));
                return existing;
            }
            if (existing.getStatus() == TurnExecutionRecord.Status.COMPLETED) {
                List<ClarificationStore.Record> rebound = existing.getChallenges();
                if (sessionAccess.tentativeSession() != null) {
                    byte[] newTokenHash = sessionAccess.tentativeSession().tokenHash();
                    clarificationStore.rebindLiveChallenges(
                            conversationId, newTokenHash, now, 32);
                    sessionStore.save(sessionAccess.tentativeSession());
                    rebound = existing.getChallenges().stream()
                            .map(challenge -> rebind(challenge, newTokenHash)).toList();
                }
                byte[] currentTokenHash = sessionAccess.tentativeSession() != null
                        ? sessionAccess.tentativeSession().tokenHash()
                        : sessionAccess.tokenHash();
                ConversationSessionStore.Session currentSession =
                        sessionStore.find(
                                List.of(currentTokenHash), now, deadline)
                                .orElse(null);
                result.set(ClaimResult.replay(
                        existing.getPublicSnapshot(), currentSession));
                return TurnExecutionRecord.restore(
                        existing.getRequestId(), existing.getConversationId(),
                        fingerprints.current(), fingerprints.currentKeyId(),
                        TurnExecutionRecord.Status.COMPLETED,
                        existing.getLeaseExpiresAt(), existing.getPublicSnapshot(),
                        existing.getContexts(), rebound, existing.getTerminalAt());
            }
            if (existing.getStatus() == TurnExecutionRecord.Status.CANCELLED) {
                result.set(ClaimResult.state(ClaimResult.Status.CANCELLED));
                return existing;
            }
            if (now.isBefore(existing.getLeaseExpiresAt())) {
                long seconds = Math.max(1, Duration.between(now, existing.getLeaseExpiresAt()).toSeconds());
                result.set(ClaimResult.inProgress(seconds));
                return existing;
            }
            result.set(ClaimResult.claimed());
            return TurnExecutionRecord.claimed(
                    requestId, conversationId, fingerprints.current(),
                    fingerprints.currentKeyId(), now.plus(leaseDuration));
        });
        return result.get();
    }

    @Override public synchronized boolean complete(
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

    @Override public synchronized boolean complete(
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

    /**
     * 单一终态闸门：仅当记录仍处于 CLAIMED、指纹恒定时间相等且未绝对过期时才
     * 提交；提交在同一临界区内完成 会话落库 → 讨论变更 → challenge 写入 →
     * 澄清预留消费 → 终态迁移，任一步失败即整体不提交（预留丢失则抛错，因属
     * 并发异常状态）。
     */
    @Override public synchronized boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation) {
        if (deadline.isExpired()) return false;
        if (!authorizeSettlementSession(sessionAccess, sessionToCreate, completedAt)) return false;
        TurnExecutionRecord existing = records.get(requestId);
        Instant absoluteExpiry = absoluteExpiries.get(requestId);
        if (existing == null
                || existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                || !MessageDigest.isEqual(
                existing.getRequestFingerprint(), fingerprint)
                || absoluteExpiry == null
                || !completedAt.isBefore(absoluteExpiry)) {
            return false;
        }
        if (!clarificationMutation.isNone()
                && !clarificationStore.canCommitReservation(
                clarificationMutation.clarificationId(), requestId,
                clarificationMutation.answer(), completedAt)) {
            return false;
        }
        if (sessionToCreate != null) sessionStore.save(sessionToCreate);
        byte[] tokenHash = sessionAccess.tokenHash() != null
                ? sessionAccess.tokenHash()
                : sessionToCreate.tokenHash();
        if (!sessionStore.applyDiscussionMutation(
                existing.getConversationId(), tokenHash,
                discussionMutation, completedAt)) {
            return false;
        }
        clarificationStore.saveAllAtomically(challenges);
        if (!clarificationMutation.isNone()
                && !clarificationStore.commitReservation(
                clarificationMutation.clarificationId(), requestId,
                clarificationMutation.answer(), completedAt)) {
            throw new IllegalStateException(
                    "clarification reservation changed during settlement");
        }
        records.put(requestId, existing.completed(
                snapshot, contexts, challenges, completedAt));
        return true;
    }

    /** 基础 complete 的会话回读包装：提交成功后返回结算后会话快照。 */
    @Override public synchronized SettlementResult completeWithSession(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation) {
        boolean completed = complete(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation, clarificationMutation);
        if (!completed) return new SettlementResult(false, null);
        byte[] tokenHash = sessionAccess.tokenHash() != null
                ? sessionAccess.tokenHash() : sessionToCreate.tokenHash();
        return new SettlementResult(
                true, sessionStore.find(
                List.of(tokenHash), completedAt, deadline).orElse(null));
    }

    /** 带语义状态的结算：提交成功后原子替换会话语义状态并回读会话。 */
    @Override public synchronized SettlementResult completeWithSession(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation,
            com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarificationMutation,
            com.portfolio.agent.turn.continuation.ConversationSemanticState semanticState) {
        SettlementResult settled = completeWithSession(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                discussionMutation, clarificationMutation);
        if (!settled.completed() || semanticState == null) return settled;
        byte[] tokenHash = sessionAccess.tokenHash() != null
                ? sessionAccess.tokenHash() : sessionToCreate.tokenHash();
        if (!sessionStore.replaceSemanticState(
                sessionAccess.conversationId(), tokenHash, semanticState, completedAt)) {
            throw new IllegalStateException("semantic state settlement failed");
        }
        return new SettlementResult(true, sessionStore.find(
                List.of(tokenHash), completedAt, deadline).orElse(null));
    }

    /** 取消仍处于 CLAIMED 的 Turn，并释放其持有的澄清预留。 */
    @Override public synchronized boolean cancel(
            UUID requestId, String conversationId, Instant cancelledAt) {
        AtomicBoolean cancelled = new AtomicBoolean();
        records.computeIfPresent(requestId, (key, existing) -> {
            if (existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                    || !existing.getConversationId().equals(conversationId)
                    || !cancelledAt.isBefore(absoluteExpiries.get(requestId))) return existing;
            cancelled.set(true);
            return existing.cancelled(cancelledAt);
        });
        if (cancelled.get()) {
            clarificationStore.releaseReservations(requestId);
        }
        return cancelled.get();
    }

    /** 按绝对过期时间读取未过期记录。 */
    @Override public Optional<TurnExecutionRecord> find(UUID requestId) {
        TurnExecutionRecord record = records.get(requestId);
        Instant expiresAt = absoluteExpiries.get(requestId);
        return record == null || expiresAt == null || !clock.instant().isBefore(expiresAt)
                ? Optional.empty() : Optional.of(record);
    }
    /** 凭证吊销会话并清除其全部记录与 challenge。 */
    @Override public synchronized boolean clearConversation(
            String conversationId, byte[] tokenHash, Instant clearedAt) {
        if (!sessionStore.revokeIfMatches(conversationId, tokenHash, clearedAt)) return false;
        records.entrySet().removeIf(value ->
                value.getValue().getConversationId().equals(conversationId));
        absoluteExpiries.keySet().removeIf(requestId -> !records.containsKey(requestId));
        clarificationStore.clear(conversationId);
        return true;
    }
    /** 在已完成记录的上下文中按 Handle 查找未过期 ContinuationContext。 */
    @Override public Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("agent state deadline exceeded");
        return records.values().stream()
                .filter(value -> value.getStatus() == TurnExecutionRecord.Status.COMPLETED)
                .filter(value -> value.getConversationId().equals(conversationId))
                .filter(value -> now.isBefore(absoluteExpiries.get(value.getRequestId())))
                .flatMap(value -> value.getContexts().stream())
                .filter(value -> value.getContextHandle().equals(contextHandle))
                .filter(value -> now.isBefore(value.getExpiresAt())).findFirst();
    }
    /** 澄清预留：deadline 内委托 ClarificationStore 的原子预留。 */
    @Override public synchronized ClarificationStore.ReserveResult reserveClarification(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId,
            ClarificationStore.ClarificationAnswer answer,
            UUID requestId, Instant reservationExpiresAt, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("agent state deadline exceeded");
        return clarificationStore.reserve(
                clarificationId, conversationId, resumeTokenHash,
                currentContentReleaseId, answer,
                requestId, reservationExpiresAt);
    }

    /** 批量清理绝对过期的执行、challenge 与会话（总预算 limit）。 */
    public CleanupResult cleanup(Instant now, int limit) {
        if (limit < 1) return new CleanupResult(0, 0, 0);
        int executions = 0;
        for (java.util.Map.Entry<UUID, Instant> entry : absoluteExpiries.entrySet()) {
            if (executions >= limit) break;
            if (!now.isBefore(entry.getValue())
                    && absoluteExpiries.remove(entry.getKey(), entry.getValue())) {
                records.remove(entry.getKey());
                executions++;
            }
        }
        int challenges = clarificationStore.cleanup(now, limit - executions);
        int sessions = sessionStore.cleanup(now, limit - executions - challenges);
        return new CleanupResult(executions, challenges, sessions);
    }

    /** 清理计数：执行、challenge、会话三类删除量。 */
    public record CleanupResult(int executions, int challenges, int sessions) {
        public int total() { return executions + challenges + sessions; }
    }

    /** Claim 期会话校验：试探性会话走 authorizeTentative，已认证会话走哈希匹配。 */
    private boolean authorizeSession(
            String conversationId, SessionAccess access, Instant now) {
        if (!conversationId.equals(access.conversationId())) return false;
        if (access.tentativeSession() != null) {
            return sessionStore.authorizeTentative(access.tentativeSession(), now);
        }
        return sessionStore.authorize(conversationId, access.tokenHash(), now);
    }

    /** 结算期会话校验：试探性会话必须同时提交待建会话；已认证会话不得另建会话。 */
    private boolean authorizeSettlementSession(
            SessionAccess access, ConversationSessionStore.Session sessionToCreate,
            Instant now) {
        if (access.tentativeSession() != null) {
            return sessionToCreate != null
                    && sessionToCreate.conversationId().equals(access.conversationId())
                    && sessionStore.authorizeTentative(sessionToCreate, now);
        }
        return sessionToCreate == null
                && sessionStore.authorize(access.conversationId(), access.tokenHash(), now);
    }

    /** 把 challenge 记录的 ResumeToken 哈希替换为新会话的哈希（重放时换绑）。 */
    private ClarificationStore.Record rebind(
            ClarificationStore.Record current, byte[] tokenHash) {
        return new ClarificationStore.Record(
                current.conversationId(), tokenHash, current.contentReleaseId(),
                current.challenge(), current.choiceBindings(), current.textBindings(),
                current.resumeTemplate());
    }
}
