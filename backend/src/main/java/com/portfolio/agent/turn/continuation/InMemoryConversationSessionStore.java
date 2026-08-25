package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储：IN_MEMORY 模式的 {@link ConversationSessionStore} 实现。
 *
 * <p>仅用于快速测试与定向诊断；标准本地开发与生产使用 PostgreSQL。
 * 额外提供授权校验、会话吊销、讨论状态变更与语义状态替换等原子操作，
 * 供 Turn 生命周期内的 State 边界使用。</p>
 */
public final class InMemoryConversationSessionStore implements ConversationSessionStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> revokedConversations =
            new ConcurrentHashMap<>();
    /**
     * 按哈希候选查找会话；已吊销或已过期的会话视为不存在。
     *
     * @throws IllegalStateException 查找前 Turn 预算已耗尽
     */
    @Override public Optional<Session> find(
            java.util.List<byte[]> tokenHashes, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("session lookup deadline exceeded");
        for (byte[] tokenHash : tokenHashes) {
            Session session = sessions.get(key(tokenHash));
            if (session != null && !revokedConversations.containsKey(session.conversationId())
                    && now.isBefore(session.expiresAt())) return Optional.of(session);
        }
        return Optional.empty();
    }
    /**
     * 保存会话行，区分首存、合并续写与"过期后新身份替换"三种情形。
     *
     * <p>同一会话的新令牌哈希在旧会话过期后写入视为身份替换：讨论指针与
     * 语义状态清空、修订号 +1 并解除吊销；旧会话未过期时保持原时间窗与
     * 状态。已吊销且非替换的写入被忽略。</p>
     */
    @Override public synchronized void save(Session session) {
        Session existing = sessions.values().stream()
                .filter(value -> value.conversationId().equals(session.conversationId()))
                .findFirst().orElse(null);
        Instant revokedAt = revokedConversations.get(session.conversationId());
        boolean replacement = existing != null
                && !session.createdAt().isBefore(existing.expiresAt())
                && !java.security.MessageDigest.isEqual(
                session.tokenHash(), existing.tokenHash());
        if (revokedAt != null && !replacement) return;
        Session effective = existing == null ? session
                : replacement ? new Session(
                session.conversationId(), session.tokenHash(),
                session.createdAt(), session.expiresAt(), null,
                existing.discussionRevision() + 1, null)
                : new Session(
                session.conversationId(), session.tokenHash(),
                existing.createdAt(), existing.expiresAt(),
                existing.activeDiscussion().orElse(null),
                existing.discussionRevision(), existing.semanticState());
        sessions.entrySet().removeIf(value ->
                value.getValue().conversationId().equals(session.conversationId()));
        if (replacement) revokedConversations.remove(session.conversationId());
        sessions.put(key(effective.tokenHash()), effective);
    }
    /** 测试辅助：吊销指定会话。 */
    synchronized void revokeForTest(String conversationId) {
        revokedConversations.put(conversationId, Instant.now());
    }
    /** 校验会话与令牌哈希当前是否有效：未吊销、未过期且哈希常数时间相等。 */
    public synchronized boolean authorize(
            String conversationId, byte[] tokenHash, Instant now) {
        if (revokedConversations.containsKey(conversationId)) return false;
        Session session = sessions.values().stream()
                .filter(value -> value.conversationId().equals(conversationId))
                .findFirst().orElse(null);
        return session != null && now.isBefore(session.expiresAt())
                && java.security.MessageDigest.isEqual(session.tokenHash(), tokenHash);
    }
    /** 校验临时会话是否可写入：旧会话已过期，或构成过期后的新身份替换。 */
    public synchronized boolean authorizeTentative(Session tentative, Instant now) {
        Session existing = sessions.values().stream()
                .filter(value -> value.conversationId().equals(tentative.conversationId()))
                .findFirst().orElse(null);
        Instant revokedAt = revokedConversations.get(tentative.conversationId());
        if (revokedAt != null && existing != null
                && now.isBefore(existing.expiresAt())) return false;
        return existing == null || now.isBefore(existing.expiresAt())
                || !tentative.createdAt().isBefore(existing.expiresAt())
                && !java.security.MessageDigest.isEqual(
                tentative.tokenHash(), existing.tokenHash());
    }
    /** 当令牌匹配且有效时吊销会话（用于清除当前匿名会话）。 */
    public synchronized boolean revokeIfMatches(
            String conversationId, byte[] tokenHash, Instant clearedAt) {
        if (!authorize(conversationId, tokenHash, clearedAt)) return false;
        revokedConversations.put(conversationId, clearedAt);
        return true;
    }
    /**
     * 授权后原子应用讨论状态变更（带代数守卫）；REPLACE/CLEAR 使修订号 +1。
     */
    public synchronized boolean applyDiscussionMutation(
            String conversationId, byte[] tokenHash,
            DiscussionStateMutation mutation, Instant now) {
        if (mutation.isNone()) return true;
        if (!authorize(conversationId, tokenHash, now)) return false;
        java.util.Map.Entry<String, Session> entry = sessions.entrySet().stream()
                .filter(value -> value.getValue().conversationId()
                        .equals(conversationId))
                .findFirst().orElse(null);
        if (entry == null) return false;
        Session currentSession = entry.getValue();
        ActiveDiscussionPointer current =
                currentSession.activeDiscussion().orElse(null);
        if (!mutation.matches(current)) return false;
        Session updated = new Session(
                currentSession.conversationId(),
                currentSession.tokenHash(),
                currentSession.createdAt(),
                currentSession.expiresAt(),
                mutation.result(current),
                currentSession.discussionRevision()
                        + (mutation.getKind()
                        == DiscussionStateMutation.Kind.REPLACE
                        || mutation.getKind()
                        == DiscussionStateMutation.Kind.CLEAR ? 1 : 0),
                currentSession.semanticState());
        sessions.put(entry.getKey(), updated);
        return true;
    }
    /** 授权后原子替换会话的语义状态，其余字段保持不变。 */
    public synchronized boolean replaceSemanticState(
            String conversationId, byte[] tokenHash,
            ConversationSemanticState semanticState, Instant now) {
        if (!authorize(conversationId, tokenHash, now)) return false;
        java.util.Map.Entry<String, Session> entry = sessions.entrySet().stream()
                .filter(value -> value.getValue().conversationId().equals(conversationId))
                .findFirst().orElse(null);
        if (entry == null) return false;
        Session current = entry.getValue();
        sessions.put(entry.getKey(), new Session(
                current.conversationId(), current.tokenHash(), current.createdAt(),
                current.expiresAt(), current.activeDiscussion().orElse(null),
                current.discussionRevision(), semanticState));
        return true;
    }
    /** 删除至多 limit 条过期会话（连同其吊销记录），返回删除数量。 */
    public synchronized int cleanup(Instant now, int limit) {
        if (limit < 1) return 0;
        int removed = 0;
        for (java.util.Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (removed >= limit) break;
            if (!now.isBefore(entry.getValue().expiresAt())
                    && sessions.remove(entry.getKey(), entry.getValue())) {
                revokedConversations.remove(entry.getValue().conversationId());
                removed++;
            }
        }
        return removed;
    }
    private String key(byte[] hash) { return Base64.getUrlEncoder().withoutPadding().encodeToString(hash); }
}
