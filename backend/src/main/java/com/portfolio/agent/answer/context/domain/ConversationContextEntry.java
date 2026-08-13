package com.portfolio.agent.answer.context.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable snapshot of one server-side Context entry. */
public final class ConversationContextEntry {
    private final ConversationId conversationId;
    private final ContextHandle contextHandle;
    private final ConversationContextValue value;
    private final ContextHandle parentContextHandle;
    private final String sourceTaskId;
    private final int payloadBytes;
    private final Instant createdAt;
    private final Instant lastAccessedAt;
    private final Instant idleExpiresAt;
    private final Instant absoluteExpiresAt;

    public ConversationContextEntry(
            ConversationId conversationId,
            ContextHandle contextHandle,
            ConversationContextValue value,
            ContextHandle parentContextHandle,
            String sourceTaskId,
            int payloadBytes,
            Instant createdAt,
            Instant lastAccessedAt,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.contextHandle = Objects.requireNonNull(contextHandle, "contextHandle");
        this.value = Objects.requireNonNull(value, "value");
        this.parentContextHandle = parentContextHandle;
        this.sourceTaskId = requireText(sourceTaskId, "sourceTaskId");
        if (payloadBytes < 1 || payloadBytes > 16 * 1024) {
            throw new IllegalArgumentException("payloadBytes must be between 1 and 16384");
        }
        this.payloadBytes = payloadBytes;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastAccessedAt = Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
        this.idleExpiresAt = Objects.requireNonNull(idleExpiresAt, "idleExpiresAt");
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
        if (idleExpiresAt.isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException("idle expiry cannot exceed absolute expiry");
        }
    }

    public ConversationId getConversationId() { return conversationId; }
    public ContextHandle getContextHandle() { return contextHandle; }
    public ConversationContextType getContextType() { return value.getType(); }
    public ConversationContextValue getValue() { return value; }
    public ContextHandle getParentContextHandle() { return parentContextHandle; }
    public String getSourceTaskId() { return sourceTaskId; }
    public int getPayloadBytes() { return payloadBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public Instant getIdleExpiresAt() { return idleExpiresAt; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    @Override
    public String toString() {
        return "ConversationContextEntry{type=" + getContextType()
                + ", payloadBytes=" + payloadBytes + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
