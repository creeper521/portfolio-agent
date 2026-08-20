package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Objects;

/** Session-row pointer; status is always derived from its expiry. */
public final class ActiveDiscussionPointer {
    private final String contextHandle;
    private final String projectId;
    private final Instant contextExpiresAt;

    public ActiveDiscussionPointer(
            String contextHandle,
            String projectId,
            Instant contextExpiresAt) {
        this.contextHandle = ContinuationContext.text(
                contextHandle, "contextHandle");
        this.projectId = ContinuationContext.text(projectId, "projectId");
        this.contextExpiresAt = Objects.requireNonNull(
                contextExpiresAt, "contextExpiresAt");
    }

    public String getContextHandle() { return contextHandle; }
    public String getProjectId() { return projectId; }
    public Instant getContextExpiresAt() { return contextExpiresAt; }

    public Status statusAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(contextExpiresAt)
                ? Status.ACTIVE : Status.EXPIRED;
    }

    public boolean matchesGeneration(String expectedHandle) {
        return contextHandle.equals(expectedHandle);
    }

    public enum Status {
        ACTIVE,
        EXPIRED
    }
}
