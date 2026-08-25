package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.DiscussionStateMutation;
import com.portfolio.agent.turn.continuation.ClarificationSettlementMutation;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turn 结算的原子状态权威：Claim → Complete/Cancel 的排他迁移。
 *
 * <p>每个 Turn 只允许一次终态迁移；Complete 在一个原子步骤内同时写入公众快照、
 * ContinuationContext、challenge、会话与讨论状态，任一前置校验失败则整体失败。
 * 所有带 {@link TurnDeadline} 的操作都必须在截止时间前完成。实现不得持久化访客
 * 问题、Prompt、原始模型输出或 ConversationWindow；可写入的只有加密 typed state
 * 与 persistence-safe 回放体。</p>
 */
public interface TurnExecutionStore {
    /**
     * 认领或重放一个 Turn。
     *
     * <p>同一 requestId + 匹配指纹的重复请求：已完成则返回 REPLAY（携带可重放快照），
     * 未完成且租约未过期则返回 IN_PROGRESS（携带建议等待秒数）；指纹不匹配或跨会话
     * 返回 CONFLICT；已取消返回 CANCELLED；租约过期允许重新认领。</p>
     *
     * @param fingerprints 当前与轮换窗口内的可接受指纹集
     * @param sessionAccess 已认证或试探性会话访问权
     * @param leaseDuration 认领租期
     */
    ClaimResult claim(
            UUID requestId, String conversationId, RequestFingerprintSet fingerprints,
            SessionAccess sessionAccess, Instant now, Duration leaseDuration,
            TurnDeadline deadline);
    /**
     * 原子结算：写入公众快照、上下文、challenge 与（可选的）新会话。
     *
     * @return 仅当记录仍处于 CLAIMED 且指纹恒定时间相等时才提交并返回 true
     */
    boolean complete(
            UUID requestId, byte[] requestFingerprint, PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline);
    /**
     * 带讨论状态变更的结算默认实现：无变更时委托给基础 complete，
     * 有变更时抛出 UnsupportedOperationException（由支持讨论的 Store 覆写）。
     */
    default boolean complete(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation) {
        if (discussionMutation.isNone()) {
            return complete(
                    requestId, requestFingerprint, publicSnapshot,
                    contexts, challenges, sessionToCreate,
                    sessionAccess, completedAt, deadline);
        }
        throw new UnsupportedOperationException(
                "discussion settlement is unavailable");
    }
    /**
     * 带澄清消费变更的结算默认实现：无变更时逐级委托，有变更时抛出
     * UnsupportedOperationException（由支持澄清预留的 Store 覆写）。
     */
    default boolean complete(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation) {
        if (clarificationMutation.isNone()) {
            return complete(
                    requestId, requestFingerprint, publicSnapshot,
                    contexts, challenges, sessionToCreate, sessionAccess,
                    completedAt, deadline, discussionMutation);
        }
        throw new UnsupportedOperationException(
                "clarification settlement is unavailable");
    }
    /**
     * 结算并回读结算后会话快照。默认实现委托给 complete，不返回会话
     * （由支持会话投影的 Store 覆写）。
     */
    default SettlementResult completeWithSession(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation) {
        return new SettlementResult(complete(
                requestId, requestFingerprint, publicSnapshot,
                contexts, challenges, sessionToCreate, sessionAccess,
                completedAt, deadline, discussionMutation,
                clarificationMutation), null);
    }
    /**
     * 带会话语义状态（semanticState）的结算默认实现：无语义状态时逐级委托，
     * 有语义状态时抛出 UnsupportedOperationException（由支持的 Store 覆写）。
     */
    default SettlementResult completeWithSession(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation,
            ConversationSemanticState semanticState) {
        if (semanticState == null) {
            return completeWithSession(
                    requestId, requestFingerprint, publicSnapshot, contexts,
                    challenges, sessionToCreate, sessionAccess, completedAt,
                    deadline, discussionMutation, clarificationMutation);
        }
        throw new UnsupportedOperationException(
                "semantic state settlement is unavailable");
    }
    /**
     * 取消一个仍处于 CLAIMED 的 Turn。
     *
     * <p>取消成功时一并释放该请求持有的澄清预留；记录已过期则直接删除。</p>
     *
     * @return 是否真正发生了 CLAIMED → CANCELLED 迁移
     */
    boolean cancel(UUID requestId, String conversationId, Instant cancelledAt);

    /** 按 requestId 查找未过期记录（含终态快照与上下文），过期或不存在返回 empty。 */
    Optional<TurnExecutionRecord> find(UUID requestId);

    /**
     * 凭 ResumeToken 哈希清空当前匿名会话：吊销会话并删除该会话全部 Turn 记录。
     *
     * @return 凭证有效且会话存活时返回 true
     */
    boolean clearConversation(
            String conversationId, byte[] tokenHash, Instant clearedAt);

    /**
     * 一次 State 访问的会话凭证：要么是已认证的 conversationId + tokenHash，
     * 要么是一条待落库的试探性新会话，二者互斥且必有其一。
     */
    record SessionAccess(
            String conversationId, byte[] tokenHash,
            ConversationSessionStore.Session tentativeSession) {
        public SessionAccess {
            tokenHash = tokenHash == null ? null : tokenHash.clone();
            if (tokenHash != null && tentativeSession != null) {
                throw new IllegalArgumentException("session access is ambiguous");
            }
            if (tentativeSession != null) conversationId = tentativeSession.conversationId();
            if (!isBlankOrNull(conversationId) && conversationId.length() > 128) {
                throw new IllegalArgumentException("conversationId is invalid");
            }
            if (tokenHash == null && tentativeSession == null) {
                throw new IllegalArgumentException("session access is required");
            }
        }
        /** 已认证会话：以现有 conversationId 与 ResumeToken 哈希访问。 */
        public static SessionAccess authenticated(String conversationId, byte[] tokenHash) {
            return new SessionAccess(java.util.Objects.requireNonNull(conversationId),
                    java.util.Objects.requireNonNull(tokenHash), null);
        }
        /** 试探性新会话：结算成功时才落库该 Session。 */
        public static SessionAccess tentative(ConversationSessionStore.Session session) {
            return new SessionAccess(session.conversationId(), null,
                    java.util.Objects.requireNonNull(session));
        }
        @Override public byte[] tokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
        private static boolean isBlankOrNull(String value) {
            return value == null || value.isBlank();
        }
    }

    /** Claim 的五种结果状态：认领成功、可重放、进行中、指纹冲突、已取消。 */
    record ClaimResult(
            Status status, PublicAgentTurn replay,
            long retryAfterSeconds,
            ConversationSessionStore.Session sessionSnapshot) {
        /** Claim 结果状态；IN_PROGRESS 携带建议等待秒数，REPLAY 携带重放快照。 */
        public enum Status { CLAIMED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED }
        public static ClaimResult claimed() { return new ClaimResult(Status.CLAIMED, null, 0, null); }
        public static ClaimResult replay(PublicAgentTurn replay) { return replay(replay, null); }
        public static ClaimResult replay(
                PublicAgentTurn replay,
                ConversationSessionStore.Session sessionSnapshot) {
            return new ClaimResult(Status.REPLAY, replay, 0, sessionSnapshot);
        }
        public static ClaimResult state(Status status) { return new ClaimResult(status, null, 0, null); }
        public static ClaimResult inProgress(long retryAfter) {
            return new ClaimResult(
                    Status.IN_PROGRESS, null,
                    Math.max(1, retryAfter), null);
        }
    }
    /** 结算结果：是否提交成功，以及提交后的会话快照（可为 null）。 */
    record SettlementResult(
            boolean completed,
            ConversationSessionStore.Session sessionSnapshot) { }
}
