package com.portfolio.agent.portfolio.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PortfolioHomeResponse {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerResponse owner;
    private final List<ProjectSummaryResponse> projects;

    public PortfolioHomeResponse(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerResponse owner,
            List<ProjectSummaryResponse> projects
    ) {
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
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

    public List<ProjectSummaryResponse> getProjects() {
        return projects;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioHomeResponse that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects);
    }

    @Override
    public String toString() {
        return "PortfolioHomeResponse{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                '}';
    }
}
