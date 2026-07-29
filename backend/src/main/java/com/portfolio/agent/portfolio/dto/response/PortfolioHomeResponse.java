package com.portfolio.agent.portfolio.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PortfolioHomeResponse {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerResponse owner;
    private final List<ProjectSummaryResponse> projects;
    private final List<CaseCollectionResponse> collections;

    public PortfolioHomeResponse(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerResponse owner,
            List<ProjectSummaryResponse> projects,
            List<CaseCollectionResponse> collections
    ) {
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.collections = List.copyOf(collections);
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

    public List<CaseCollectionResponse> getCollections() {
        return collections;
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
                && Objects.equals(projects, that.projects)
                && Objects.equals(collections, that.collections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects, collections);
    }

    @Override
    public String toString() {
        return "PortfolioHomeResponse{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", collections=" + collections +
                '}';
    }
}
