package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class CaseStudy {

    private final String id;
    private final String code;
    private final String slug;
    private final CaseType type;
    private final String title;
    private final String summary;
    private final String problem;
    private final List<String> actions;
    private final List<String> decisions;
    private final List<String> verification;
    private final String outcome;
    private final List<String> limitations;
    private final AchievementStatus achievementStatus;
    private final ContributionType contributionType;
    private final String projectId;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final List<String> timelineEventIds;
    private final List<String> questionPresetIds;

    @JsonCreator
    public CaseStudy(
            @JsonProperty("id") String id,
            @JsonProperty("code") String code,
            @JsonProperty("slug") String slug,
            @JsonProperty("type") CaseType type,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("problem") String problem,
            @JsonProperty("actions") List<String> actions,
            @JsonProperty("decisions") List<String> decisions,
            @JsonProperty("verification") List<String> verification,
            @JsonProperty("outcome") String outcome,
            @JsonProperty("limitations") List<String> limitations,
            @JsonProperty("achievementStatus") AchievementStatus achievementStatus,
            @JsonProperty("contributionType") ContributionType contributionType,
            @JsonProperty("projectId") String projectId,
            @JsonProperty("claimIds") List<String> claimIds,
            @JsonProperty("evidenceIds") List<String> evidenceIds,
            @JsonProperty("timelineEventIds") List<String> timelineEventIds,
            @JsonProperty("questionPresetIds") List<String> questionPresetIds
    ) {
        this.id = id;
        this.code = code;
        this.slug = slug;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.problem = problem;
        this.actions = List.copyOf(actions);
        this.decisions = List.copyOf(decisions);
        this.verification = List.copyOf(verification);
        this.outcome = outcome;
        this.limitations = List.copyOf(limitations);
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.projectId = projectId;
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.timelineEventIds = List.copyOf(timelineEventIds);
        this.questionPresetIds = List.copyOf(questionPresetIds);
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getSlug() { return slug; }
    public CaseType getType() { return type; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getProblem() { return problem; }
    public List<String> getActions() { return actions; }
    public List<String> getDecisions() { return decisions; }
    public List<String> getVerification() { return verification; }
    public String getOutcome() { return outcome; }
    public List<String> getLimitations() { return limitations; }
    public AchievementStatus getAchievementStatus() { return achievementStatus; }
    public ContributionType getContributionType() { return contributionType; }
    public String getProjectId() { return projectId; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getTimelineEventIds() { return timelineEventIds; }
    public List<String> getQuestionPresetIds() { return questionPresetIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaseStudy that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(code, that.code)
                && Objects.equals(slug, that.slug)
                && type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(problem, that.problem)
                && Objects.equals(actions, that.actions)
                && Objects.equals(decisions, that.decisions)
                && Objects.equals(verification, that.verification)
                && Objects.equals(outcome, that.outcome)
                && Objects.equals(limitations, that.limitations)
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(claimIds, that.claimIds)
                && Objects.equals(evidenceIds, that.evidenceIds)
                && Objects.equals(timelineEventIds, that.timelineEventIds)
                && Objects.equals(questionPresetIds, that.questionPresetIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, slug, type, title, summary, problem, actions, decisions,
                verification, outcome, limitations, achievementStatus, contributionType, projectId,
                claimIds, evidenceIds, timelineEventIds, questionPresetIds);
    }

    @Override
    public String toString() {
        return "CaseStudy{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", slug='" + slug + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", problem='" + problem + '\'' +
                ", actions=" + actions +
                ", decisions=" + decisions +
                ", verification=" + verification +
                ", outcome='" + outcome + '\'' +
                ", limitations=" + limitations +
                ", achievementStatus=" + achievementStatus +
                ", contributionType=" + contributionType +
                ", projectId='" + projectId + '\'' +
                ", claimIds=" + claimIds +
                ", evidenceIds=" + evidenceIds +
                ", timelineEventIds=" + timelineEventIds +
                ", questionPresetIds=" + questionPresetIds +
                '}';
    }
}
