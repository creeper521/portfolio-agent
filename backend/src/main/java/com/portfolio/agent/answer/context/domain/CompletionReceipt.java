package com.portfolio.agent.answer.context.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Public completion state retained for idempotent retries; it never contains answer text. */
public final class CompletionReceipt {
    private final UUID requestToken;
    private final ConversationId conversationId;
    private final RequestFingerprint fingerprint;
    private final ContextHandle contextHandle;
    private final ConversationContinuationStatus continuationStatus;
    private final Instant completedAt;

    public CompletionReceipt(
            UUID requestToken,
            ConversationId conversationId,
            RequestFingerprint fingerprint,
            ContextHandle contextHandle,
            ConversationContinuationStatus continuationStatus,
            Instant completedAt) {
        this.requestToken = Objects.requireNonNull(requestToken, "requestToken");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.contextHandle = contextHandle;
        this.continuationStatus = Objects.requireNonNull(continuationStatus, "continuationStatus");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    public UUID getRequestToken() { return requestToken; }
    public ConversationId getConversationId() { return conversationId; }
    public RequestFingerprint getFingerprint() { return fingerprint; }
    public Optional<ContextHandle> getContextHandle() { return Optional.ofNullable(contextHandle); }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }
    public Instant getCompletedAt() { return completedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CompletionReceipt that)) return false;
        return requestToken.equals(that.requestToken)
                && conversationId.equals(that.conversationId)
                && fingerprint.equals(that.fingerprint)
                && Objects.equals(contextHandle, that.contextHandle)
                && continuationStatus == that.continuationStatus
                && completedAt.equals(that.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestToken, conversationId, fingerprint, contextHandle,
                continuationStatus, completedAt);
    }

    @Override
    public String toString() {
        return "CompletionReceipt{continuationStatus=" + continuationStatus
                + ", hasContextHandle=" + (contextHandle != null) + '}';
    }
}
