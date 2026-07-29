package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.CareerTrack;
import com.portfolio.agent.portfolio.domain.ProjectDisplayTier;
import com.portfolio.agent.portfolio.domain.ProjectNature;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;

import java.util.Objects;

public final class ProjectSummaryResponse {

    private final String slug;
    private final String title;
    private final String summary;
    private final ProjectStatus status;
    private final ContributionType contributionType;
    private final CareerTrack careerTrack;
    private final ProjectNature projectNature;
    private final ProjectDisplayTier displayTier;
    private final int caseCount;

    public ProjectSummaryResponse(
            String slug,
            String title,
            String summary,
            ProjectStatus status,
            ContributionType contributionType,
            CareerTrack careerTrack,
            ProjectNature projectNature,
            ProjectDisplayTier displayTier,
            int caseCount
    ) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.contributionType = contributionType;
        this.careerTrack = careerTrack;
        this.projectNature = projectNature;
        this.displayTier = displayTier;
        this.caseCount = caseCount;
    }

    public static ProjectSummaryResponse from(ProjectProfile project, int caseCount) {
        return new ProjectSummaryResponse(
                project.getSlug(),
                project.getTitle(),
                project.getSummary(),
                project.getStatus(),
                project.getContributionType(),
                project.getCareerTrack(),
                project.getProjectNature(),
                project.getDisplayTier(),
                caseCount
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

    public CareerTrack getCareerTrack() {
        return careerTrack;
    }

    public ProjectNature getProjectNature() {
        return projectNature;
    }

    public ProjectDisplayTier getDisplayTier() {
        return displayTier;
    }

    public int getCaseCount() {
        return caseCount;
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
                && contributionType == that.contributionType
                && careerTrack == that.careerTrack
                && projectNature == that.projectNature
                && displayTier == that.displayTier
                && caseCount == that.caseCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, title, summary, status, contributionType, careerTrack,
                projectNature, displayTier, caseCount);
    }

    @Override
    public String toString() {
        return "ProjectSummaryResponse{" +
                "slug='" + slug + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", status=" + status +
                ", contributionType=" + contributionType +
                ", careerTrack=" + careerTrack +
                ", projectNature=" + projectNature +
                ", displayTier=" + displayTier +
                ", caseCount=" + caseCount +
                '}';
    }
}
