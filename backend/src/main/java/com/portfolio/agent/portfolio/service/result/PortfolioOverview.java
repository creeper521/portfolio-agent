package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.ProjectProfile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PortfolioOverview {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;
    private final List<CaseCollection> collections;
    private final Map<String, Integer> caseCountsByProjectId;

    public PortfolioOverview(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectProfile> projects,
            List<CaseCollection> collections,
            Map<String, Integer> caseCountsByProjectId
    ) {
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.collections = List.copyOf(collections);
        this.caseCountsByProjectId = Map.copyOf(new LinkedHashMap<>(caseCountsByProjectId));
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OwnerProfile getOwner() {
        return owner;
    }

    public List<ProjectProfile> getProjects() {
        return projects;
    }

    public List<CaseCollection> getCollections() {
        return collections;
    }

    public int getCaseCount(String projectId) {
        return caseCountsByProjectId.getOrDefault(projectId, 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioOverview that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(collections, that.collections)
                && Objects.equals(caseCountsByProjectId, that.caseCountsByProjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects, collections,
                caseCountsByProjectId);
    }

    @Override
    public String toString() {
        return "PortfolioOverview{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", collections=" + collections +
                ", caseCountsByProjectId=" + caseCountsByProjectId +
                '}';
    }

    public PortfolioOverview(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectProfile> projects
    ) {
        this(contentVersion, publishedAt, owner, projects, List.of(), Map.of());
    }
}
