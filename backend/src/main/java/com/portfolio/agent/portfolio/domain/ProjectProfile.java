package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class ProjectProfile {

    private final String id;
    private final String code;
    private final String slug;
    private final String title;
    private final String summary;
    private final String background;
    private final List<String> responsibilities;
    private final String solution;
    private final List<String> keyDecisions;
    private final List<String> technologies;
    private final List<String> verification;
    private final String outcome;
    private final String handoff;
    private final ProjectStatus status;
    private final ContributionType contributionType;
    private final CareerTrack careerTrack;
    private final ProjectNature projectNature;
    private final ProjectDisplayTier displayTier;
    private final List<String> featuredCaseIds;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final List<String> timelineEventIds;

    @JsonCreator
    public ProjectProfile(
            @JsonProperty("id") String id,
            @JsonProperty("code") String code,
            @JsonProperty("slug") String slug,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("background") String background,
            @JsonProperty("responsibilities") List<String> responsibilities,
            @JsonProperty("solution") String solution,
            @JsonProperty("keyDecisions") List<String> keyDecisions,
            @JsonProperty("technologies") List<String> technologies,
            @JsonProperty("verification") List<String> verification,
            @JsonProperty("outcome") String outcome,
            @JsonProperty("handoff") String handoff,
            @JsonProperty("status") ProjectStatus status,
            @JsonProperty("contributionType") ContributionType contributionType,
            @JsonProperty("careerTrack") CareerTrack careerTrack,
            @JsonProperty("projectNature") ProjectNature projectNature,
            @JsonProperty("displayTier") ProjectDisplayTier displayTier,
            @JsonProperty("featuredCaseIds") List<String> featuredCaseIds,
            @JsonProperty("claimIds") List<String> claimIds,
            @JsonProperty("evidenceIds") List<String> evidenceIds,
            @JsonProperty("timelineEventIds") List<String> timelineEventIds
    ) {
        this.id = id;
        this.code = code;
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.background = background;
        this.responsibilities = List.copyOf(responsibilities);
        this.solution = solution;
        this.keyDecisions = List.copyOf(keyDecisions);
        this.technologies = List.copyOf(technologies);
        this.verification = List.copyOf(verification);
        this.outcome = outcome;
        this.handoff = handoff;
        this.status = status;
        this.contributionType = contributionType;
        this.careerTrack = careerTrack == null ? CareerTrack.UNCLASSIFIED : careerTrack;
        this.projectNature = projectNature == null ? ProjectNature.UNCLASSIFIED : projectNature;
        this.displayTier = displayTier == null ? ProjectDisplayTier.PRIMARY : displayTier;
        this.featuredCaseIds = featuredCaseIds == null ? List.of() : List.copyOf(featuredCaseIds);
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.timelineEventIds = List.copyOf(timelineEventIds);
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
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

    public String getBackground() {
        return background;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public String getSolution() {
        return solution;
    }

    public List<String> getKeyDecisions() {
        return keyDecisions;
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public List<String> getVerification() {
        return verification;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getHandoff() {
        return handoff;
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

    public List<String> getFeaturedCaseIds() {
        return featuredCaseIds;
    }

    public List<String> getClaimIds() {
        return claimIds;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public List<String> getTimelineEventIds() {
        return timelineEventIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectProfile that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(code, that.code)
                && Objects.equals(slug, that.slug)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(background, that.background)
                && Objects.equals(responsibilities, that.responsibilities)
                && Objects.equals(solution, that.solution)
                && Objects.equals(keyDecisions, that.keyDecisions)
                && Objects.equals(technologies, that.technologies)
                && Objects.equals(verification, that.verification)
                && Objects.equals(outcome, that.outcome)
                && Objects.equals(handoff, that.handoff)
                && Objects.equals(status, that.status)
                && Objects.equals(contributionType, that.contributionType)
                && careerTrack == that.careerTrack
                && projectNature == that.projectNature
                && displayTier == that.displayTier
                && Objects.equals(featuredCaseIds, that.featuredCaseIds)
                && Objects.equals(claimIds, that.claimIds)
                && Objects.equals(evidenceIds, that.evidenceIds)
                && Objects.equals(timelineEventIds, that.timelineEventIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, slug, title, summary, background, responsibilities, solution,
                keyDecisions, technologies, verification, outcome, handoff, status,
                contributionType, careerTrack, projectNature, displayTier, featuredCaseIds,
                claimIds, evidenceIds, timelineEventIds);
    }

    @Override
    public String toString() {
        return "ProjectProfile{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", slug='" + slug + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", background='" + background + '\'' +
                ", responsibilities=" + responsibilities +
                ", solution='" + solution + '\'' +
                ", keyDecisions=" + keyDecisions +
                ", technologies=" + technologies +
                ", verification=" + verification +
                ", outcome='" + outcome + '\'' +
                ", handoff='" + handoff + '\'' +
                ", status=" + status +
                ", contributionType=" + contributionType +
                ", careerTrack=" + careerTrack +
                ", projectNature=" + projectNature +
                ", displayTier=" + displayTier +
                ", featuredCaseIds=" + featuredCaseIds +
                ", claimIds=" + claimIds +
                ", evidenceIds=" + evidenceIds +
                ", timelineEventIds=" + timelineEventIds +
                '}';
    }

    public ProjectProfile(
            String id,
            String code,
            String slug,
            String title,
            String summary,
            String background,
            List<String> responsibilities,
            String solution,
            List<String> keyDecisions,
            List<String> technologies,
            List<String> verification,
            String outcome,
            String handoff,
            ProjectStatus status,
            ContributionType contributionType,
            List<String> claimIds,
            List<String> evidenceIds,
            List<String> timelineEventIds
    ) {
        this(
                id, code, slug, title, summary, background, responsibilities, solution,
                keyDecisions, technologies, verification, outcome, handoff, status,
                contributionType, CareerTrack.UNCLASSIFIED, ProjectNature.UNCLASSIFIED,
                ProjectDisplayTier.PRIMARY, List.of(), claimIds, evidenceIds, timelineEventIds
        );
    }
}
