package com.portfolio.agent.answer.context.domain;

import java.util.Objects;
import java.util.Optional;

/** Result of deterministic Context resolution; it never contains a fallback answer. */
public final class ConversationContextResolution {
    public enum Status {
        RESOLVED,
        CLARIFICATION_REQUIRED,
        UNAVAILABLE,
        INVALID_REFERENCE,
        INCOMPATIBLE
    }

    public enum SelectionReason {
        EXPLICIT_HANDLE,
        UNIQUE_ACTIVE,
        MOST_RECENT_ACTIVE,
        NONE
    }

    private final Status status;
    private final SelectionReason selectionReason;
    private final ConversationContextEntry entry;

    private ConversationContextResolution(
            Status status, SelectionReason selectionReason, ConversationContextEntry entry) {
        this.status = Objects.requireNonNull(status, "status");
        this.selectionReason = Objects.requireNonNull(selectionReason, "selectionReason");
        this.entry = entry;
    }

    public static ConversationContextResolution resolved(
            SelectionReason reason, ConversationContextEntry entry) {
        return new ConversationContextResolution(
                Status.RESOLVED, Objects.requireNonNull(reason, "reason"),
                Objects.requireNonNull(entry, "entry"));
    }

    public static ConversationContextResolution unavailable() {
        return new ConversationContextResolution(Status.UNAVAILABLE, SelectionReason.NONE, null);
    }

    public static ConversationContextResolution invalidReference() {
        return new ConversationContextResolution(Status.INVALID_REFERENCE, SelectionReason.NONE, null);
    }

    public static ConversationContextResolution incompatible() {
        return new ConversationContextResolution(Status.INCOMPATIBLE, SelectionReason.NONE, null);
    }

    public static ConversationContextResolution clarificationRequired() {
        return new ConversationContextResolution(
                Status.CLARIFICATION_REQUIRED, SelectionReason.NONE, null);
    }

    public Status getStatus() { return status; }
    public SelectionReason getSelectionReason() { return selectionReason; }
    public Optional<ConversationContextEntry> getEntry() { return Optional.ofNullable(entry); }

    @Override
    public String toString() {
        return "ConversationContextResolution{status=" + status
                + ", selectionReason=" + selectionReason + '}';
    }
}
