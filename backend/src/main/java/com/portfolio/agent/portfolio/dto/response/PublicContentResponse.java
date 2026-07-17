package com.portfolio.agent.portfolio.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PublicContentResponse {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerResponse owner;
    private final List<ProjectDetailResponse> projects;
    private final List<EvidenceResponse> evidence;
    private final List<TimelineEventResponse> timeline;

    public PublicContentResponse(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerResponse owner,
            List<ProjectDetailResponse> projects,
            List<EvidenceResponse> evidence,
            List<TimelineEventResponse> timeline
    ) {
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OwnerResponse getOwner() {
        return owner;
    }

    public List<ProjectDetailResponse> getProjects() {
        return projects;
    }

    public List<EvidenceResponse> getEvidence() {
        return evidence;
    }

    public List<TimelineEventResponse> getTimeline() {
        return timeline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicContentResponse that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects, evidence, timeline);
    }

    @Override
    public String toString() {
        return "PublicContentResponse{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                '}';
    }
}
