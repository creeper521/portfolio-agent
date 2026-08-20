package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Encrypted short-lived typed authority for one locked public project. */
public final class ProjectDiscussionContext extends ContinuationContext {
    private final String projectId;
    private final Set<String> switchCandidateProjectIds;
    private final Instant startedAt;
    private final String sourceRecommendationHandle;

    public ProjectDiscussionContext(
            String contextHandle,
            String conversationId,
            String contentReleaseId,
            Instant expiresAt,
            String projectId,
            Set<String> switchCandidateProjectIds,
            Instant startedAt,
            String sourceRecommendationHandle) {
        super(contextHandle, conversationId, contentReleaseId, expiresAt);
        this.projectId = text(projectId, "projectId");
        this.switchCandidateProjectIds = Set.copyOf(
                Objects.requireNonNull(
                        switchCandidateProjectIds,
                        "switchCandidateProjectIds"));
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.sourceRecommendationHandle =
                sourceRecommendationHandle == null
                        ? null : text(
                        sourceRecommendationHandle,
                        "sourceRecommendationHandle");
        if (this.switchCandidateProjectIds.isEmpty()
                || this.switchCandidateProjectIds.size() > 5
                || this.switchCandidateProjectIds.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "switch candidate projects are invalid");
        }
        if (!this.switchCandidateProjectIds.contains(projectId)) {
            throw new IllegalArgumentException(
                    "switch scope must contain locked project");
        }
        if (!startedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException(
                    "discussion lifetime is invalid");
        }
    }

    @Override
    public Kind getKind() {
        return Kind.PROJECT_DISCUSSION;
    }

    public String getProjectId() { return projectId; }
    public Set<String> getSwitchCandidateProjectIds() {
        return switchCandidateProjectIds;
    }
    public Instant getStartedAt() { return startedAt; }
    public String getSourceRecommendationHandle() {
        return sourceRecommendationHandle;
    }
}
