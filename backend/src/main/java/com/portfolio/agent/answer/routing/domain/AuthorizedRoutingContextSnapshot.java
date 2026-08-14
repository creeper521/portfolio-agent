package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

import java.util.List;
import java.util.Objects;

/** Minimal authorized context projection consumed by routing. */
public final class AuthorizedRoutingContextSnapshot {

    private final String contextHandle;
    private final ConversationContextType contextType;
    private final String activationMode;
    private final long revision;
    private final String sourceTaskId;
    private final List<SubjectReference> subjects;
    private final String contentVersion;

    public AuthorizedRoutingContextSnapshot(
            String contextHandle,
            ConversationContextType contextType,
            String activationMode,
            long revision,
            String sourceTaskId,
            List<SubjectReference> subjects,
            String contentVersion) {
        this.contextHandle = requireText(contextHandle, "contextHandle");
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.activationMode = requireText(activationMode, "activationMode");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.revision = revision;
        this.sourceTaskId = requireText(sourceTaskId, "sourceTaskId");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        if (this.subjects.isEmpty()) {
            throw new IllegalArgumentException("subjects must not be empty");
        }
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    public String getContextHandle() { return contextHandle; }
    public ConversationContextType getContextType() { return contextType; }
    public String getActivationMode() { return activationMode; }
    public long getRevision() { return revision; }
    public String getSourceTaskId() { return sourceTaskId; }
    public List<SubjectReference> getSubjects() { return subjects; }
    public String getContentVersion() { return contentVersion; }

    @Override
    public String toString() {
        return "AuthorizedRoutingContextSnapshot{contextType=" + contextType
                + ", activationMode=" + activationMode + ", revision=" + revision
                + ", subjectCount=" + subjects.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
