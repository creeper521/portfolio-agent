package com.portfolio.agent.portfolio.api.dto;

import com.portfolio.agent.portfolio.domain.model.ContributionType;
import com.portfolio.agent.portfolio.domain.model.ProjectProfile;
import com.portfolio.agent.portfolio.domain.model.ProjectStatus;

import java.util.Objects;

public final class ProjectSummaryResponse {

    private final String slug;
    private final String title;
    private final String summary;
    private final ProjectStatus status;
    private final ContributionType contributionType;

    public ProjectSummaryResponse(
            String slug,
            String title,
            String summary,
            ProjectStatus status,
            ContributionType contributionType
    ) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.contributionType = contributionType;
    }

    public static ProjectSummaryResponse from(ProjectProfile project) {
        return new ProjectSummaryResponse(
                project.getSlug(),
                project.getTitle(),
                project.getSummary(),
                project.getStatus(),
                project.getContributionType()
        );
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectSummaryResponse that)) {
            return false;
        }
        return Objects.equals(slug, that.slug)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && status == that.status
                && contributionType == that.contributionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, title, summary, status, contributionType);
    }

    @Override
    public String toString() {
        return "ProjectSummaryResponse{" +
                "slug='" + slug + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", status=" + status +
                ", contributionType=" + contributionType +
                '}';
    }
}
