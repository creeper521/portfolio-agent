package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import com.portfolio.agent.turn.execution.TurnDeadline;

/**
 * 会话存储接口：按令牌哈希查找并保存匿名会话行。
 *
 * <p>生产实现为 PostgreSQL State，测试使用内存实现；查找受 TurnDeadline
 * 约束，防止慢查找拖垮本轮 Turn 预算。</p>
 */
public interface ConversationSessionStore {
    /**
     * 按令牌哈希候选（多密钥轮换）查找未过期会话。
     *
     * @throws IllegalStateException 查找前 Turn 预算已耗尽（内存实现）
     */
    Optional<Session> find(
            List<byte[]> tokenHashes, Instant now, TurnDeadline deadline);
    /** 保存/合并会话行。 */
    void save(Session session);

    /**
     * 会话行：会话 ID、令牌哈希、时间窗、活跃讨论指针、讨论修订号与语义状态。
     *
     * <p>tokenHash 做防御性克隆；discussionRevision 用于讨论指针的乐观
     * 并发控制；语义状态可缺失（{@link #semanticStateOptional}）。</p>
     */
    record Session(
            String conversationId, byte[] tokenHash,
            Instant createdAt, Instant expiresAt,
            ActiveDiscussionPointer activeDiscussionPointer,
            long discussionRevision,
            ConversationSemanticState semanticState) {
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt) {
            this(conversationId, tokenHash, createdAt, expiresAt, null, 0, null);
        }
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt,
                ActiveDiscussionPointer activeDiscussionPointer) {
            this(conversationId, tokenHash, createdAt, expiresAt,
                    activeDiscussionPointer, 0, null);
        }
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt,
                ActiveDiscussionPointer activeDiscussionPointer,
                long discussionRevision) {
            this(conversationId, tokenHash, createdAt, expiresAt,
                    activeDiscussionPointer, discussionRevision, null);
        }
        public Session {
            conversationId = ContinuationContext.text(conversationId, "conversationId");
            tokenHash = tokenHash.clone();
            if (!createdAt.isBefore(expiresAt)) throw new IllegalArgumentException("session expiry is invalid");
            if (discussionRevision < 0) {
                throw new IllegalArgumentException(
                        "discussionRevision must not be negative");
            }
        }
        @Override public byte[] tokenHash() { return tokenHash.clone(); }
        public Optional<ActiveDiscussionPointer> activeDiscussion() {
            return Optional.ofNullable(activeDiscussionPointer);
        }
        public Optional<ConversationSemanticState> semanticStateOptional() {
            return Optional.ofNullable(semanticState);
        }
    }
}
