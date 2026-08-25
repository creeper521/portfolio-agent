package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 单个 Turn 的 State 层不可变记录：指纹、状态、租期与终态投影。
 *
 * <p>承载 Claim → 终态的完整生命周期形状；终态形状是强不变量——COMPLETED 必须携带
 * 公众快照与 terminalAt，CANCELLED 必须携带 terminalAt，非终态不得携带快照。
 * 构造器私有，只能通过 {@link #claimed}/{@link #restore} 或状态迁移方法派生新实例。</p>
 */
public final class TurnExecutionRecord {
    private final UUID requestId;
    private final String conversationId;
    private final byte[] requestFingerprint;
    private final String fingerprintKeyId;
    private final Status status;
    private final Instant leaseExpiresAt;
    private final PublicAgentTurn publicSnapshot;
    private final List<ContinuationContext> contexts;
    private final List<ClarificationStore.Record> challenges;
    private final Instant terminalAt;

    private TurnExecutionRecord(
            UUID requestId, String conversationId, byte[] requestFingerprint,
            String fingerprintKeyId, Status status, Instant leaseExpiresAt, PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges,
            Instant terminalAt) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        this.conversationId = conversationId;
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint").clone();
        if (fingerprintKeyId == null || fingerprintKeyId.isBlank()) {
            throw new IllegalArgumentException("fingerprintKeyId is required");
        }
        this.fingerprintKeyId = fingerprintKeyId;
        this.status = Objects.requireNonNull(status, "status");
        this.leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        this.publicSnapshot = publicSnapshot;
        this.contexts = List.copyOf(contexts);
        this.challenges = List.copyOf(challenges);
        this.terminalAt = terminalAt;
        if (status == Status.COMPLETED && (publicSnapshot == null || terminalAt == null)
                || status != Status.COMPLETED && publicSnapshot != null
                || status == Status.CANCELLED && terminalAt == null) {
            throw new IllegalArgumentException("turn record terminal shape is invalid");
        }
    }

    /** 新建 CLAIMED 状态记录，租期到 leaseExpiresAt。 */
    public static TurnExecutionRecord claimed(
            UUID requestId, String conversationId, byte[] fingerprint,
            String fingerprintKeyId, Instant leaseExpiresAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, fingerprint, fingerprintKeyId,
                Status.CLAIMED, leaseExpiresAt,
                null, List.of(), List.of(), null);
    }
    /** 从持久化行重建记录（含终态快照、上下文与 challenge 副本）。 */
    public static TurnExecutionRecord restore(
            UUID requestId, String conversationId, byte[] fingerprint,
            String fingerprintKeyId, Status status, Instant leaseExpiresAt, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges, Instant terminalAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, fingerprint, fingerprintKeyId, status, leaseExpiresAt,
                snapshot, contexts, challenges, terminalAt);
    }
    /** 迁移到 COMPLETED：绑定公众快照、续跑上下文、challenge 与完成时间。 */
    public TurnExecutionRecord completed(
            PublicAgentTurn snapshot, List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges, Instant completedAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, requestFingerprint, fingerprintKeyId,
                Status.COMPLETED, leaseExpiresAt,
                snapshot, contexts, challenges, completedAt);
    }
    /** 迁移到 CANCELLED：丢弃快照与上下文，只保留取消时间。 */
    public TurnExecutionRecord cancelled(Instant cancelledAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, requestFingerprint, fingerprintKeyId,
                Status.CANCELLED, leaseExpiresAt,
                null, List.of(), List.of(), cancelledAt);
    }
    public UUID getRequestId() { return requestId; }
    public String getConversationId() { return conversationId; }
    public byte[] getRequestFingerprint() { return requestFingerprint.clone(); }
    public String getFingerprintKeyId() { return fingerprintKeyId; }
    public Status getStatus() { return status; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public PublicAgentTurn getPublicSnapshot() { return publicSnapshot; }
    public List<ContinuationContext> getContexts() { return contexts; }
    public List<ClarificationStore.Record> getChallenges() { return challenges; }
    public Instant getTerminalAt() { return terminalAt; }
    /** Turn 在 State 层的三种状态：已认领、已完成、已取消。 */
    public enum Status { CLAIMED, COMPLETED, CANCELLED }
}
