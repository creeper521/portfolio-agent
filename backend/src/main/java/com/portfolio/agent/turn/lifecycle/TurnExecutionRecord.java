package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    public static TurnExecutionRecord claimed(
            UUID requestId, String conversationId, byte[] fingerprint,
            String fingerprintKeyId, Instant leaseExpiresAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, fingerprint, fingerprintKeyId,
                Status.CLAIMED, leaseExpiresAt,
                null, List.of(), List.of(), null);
    }
    public static TurnExecutionRecord restore(
            UUID requestId, String conversationId, byte[] fingerprint,
            String fingerprintKeyId, Status status, Instant leaseExpiresAt, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges, Instant terminalAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, fingerprint, fingerprintKeyId, status, leaseExpiresAt,
                snapshot, contexts, challenges, terminalAt);
    }
    public TurnExecutionRecord completed(
            PublicAgentTurn snapshot, List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges, Instant completedAt) {
        return new TurnExecutionRecord(
                requestId, conversationId, requestFingerprint, fingerprintKeyId,
                Status.COMPLETED, leaseExpiresAt,
                snapshot, contexts, challenges, completedAt);
    }
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
    public enum Status { CLAIMED, COMPLETED, CANCELLED }
}
