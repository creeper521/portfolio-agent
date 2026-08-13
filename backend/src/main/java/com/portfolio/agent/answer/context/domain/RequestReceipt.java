package com.portfolio.agent.answer.context.domain;

import java.time.Instant;
import java.util.Objects;

/** Minimal idempotency receipt; it never stores answer text or evidence. */
public final class RequestReceipt {
    public enum Status { IN_PROGRESS, COMPLETED }
    private final ConversationId conversationId;
    private final String requestToken;
    private final RequestFingerprint fingerprint;
    private final Status status;
    private final Instant updatedAt;

    public RequestReceipt(ConversationId conversationId, String requestToken,
            RequestFingerprint fingerprint, Status status, Instant updatedAt) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.requestToken = requireText(requestToken, "requestToken");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.status = Objects.requireNonNull(status, "status");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
    public ConversationId getConversationId() { return conversationId; }
    public String getRequestToken() { return requestToken; }
    public RequestFingerprint getFingerprint() { return fingerprint; }
    public Status getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return "RequestReceipt{status=" + status + ", hasFingerprint=true}"; }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
