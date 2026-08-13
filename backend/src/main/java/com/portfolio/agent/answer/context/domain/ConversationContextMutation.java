package com.portfolio.agent.answer.context.domain;

import java.util.Objects;

/** Immutable, validated input for one Context insert and optional Active CAS. */
public final class ConversationContextMutation {
    private final ContextHandle contextHandle;
    private final ConversationContextValue value;
    private final ContextHandle parentContextHandle;
    private final String sourceTaskId;
    private final int payloadBytes;
    private final ContextSlot activeSlot;
    private final Long expectedActiveRevision;

    public ConversationContextMutation(
            ContextHandle contextHandle,
            ConversationContextValue value,
            ContextHandle parentContextHandle,
            String sourceTaskId,
            int payloadBytes,
            ContextSlot activeSlot,
            Long expectedActiveRevision) {
        this.contextHandle = Objects.requireNonNull(contextHandle, "contextHandle");
        this.value = Objects.requireNonNull(value, "value");
        this.parentContextHandle = parentContextHandle;
        this.sourceTaskId = requireText(sourceTaskId, "sourceTaskId");
        if (payloadBytes < 1 || payloadBytes > 16 * 1024) {
            throw new IllegalArgumentException("payloadBytes must be between 1 and 16384");
        }
        this.payloadBytes = payloadBytes;
        this.activeSlot = activeSlot;
        if (activeSlot != null && activeSlot.contextType() != value.getType()) {
            throw new IllegalArgumentException("active slot does not match Context type");
        }
        if (expectedActiveRevision != null && expectedActiveRevision < 0) {
            throw new IllegalArgumentException("expectedActiveRevision cannot be negative");
        }
        this.expectedActiveRevision = expectedActiveRevision;
    }

    public ContextHandle getContextHandle() { return contextHandle; }
    public ConversationContextValue getValue() { return value; }
    public ContextHandle getParentContextHandle() { return parentContextHandle; }
    public String getSourceTaskId() { return sourceTaskId; }
    public int getPayloadBytes() { return payloadBytes; }
    public ContextSlot getActiveSlot() { return activeSlot; }
    public Long getExpectedActiveRevision() { return expectedActiveRevision; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
