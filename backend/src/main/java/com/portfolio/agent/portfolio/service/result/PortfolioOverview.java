package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.ProjectProfile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PortfolioOverview {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;

    public PortfolioOverview(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectProfile> projects
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

    public OwnerProfile getOwner() {
        return owner;
    }

    public List<ProjectProfile> getProjects() {
        return projects;
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
                && Objects.equals(projects, that.projects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects);
    }

    @Override
    public String toString() {
        return "PortfolioOverview{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                '}';
    }
}
