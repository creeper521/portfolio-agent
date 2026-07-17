package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;

import java.util.List;
import java.util.Objects;

public final class ProjectDetailResponse {

    private final String slug;
    private final String code;
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
    private final List<String> evidenceIds;
    private final List<EvidenceResponse> evidence;
    private final List<String> suggestedQuestions;

    public ProjectDetailResponse(
            String slug,
            String code,
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
            List<String> evidenceIds,
            List<EvidenceResponse> evidence,
            List<String> suggestedQuestions
    ) {
        this.slug = slug;
        this.code = code;
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
        this.evidenceIds = List.copyOf(evidenceIds);
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public static ProjectDetailResponse from(
            ProjectProfile project,
            List<EvidenceResponse> evidence,
            List<String> suggestedQuestions
    ) {
        return new ProjectDetailResponse(
                project.getSlug(),
                project.getCode(),
                project.getTitle(),
                project.getSummary(),
                project.getBackground(),
                project.getResponsibilities(),
                project.getSolution(),
                project.getKeyDecisions(),
                project.getTechnologies(),
                project.getVerification(),
                project.getOutcome(),
                project.getHandoff(),
                project.getStatus(),
                project.getContributionType(),
                project.getEvidenceIds(),
                evidence,
                suggestedQuestions
        );
    }

    public String getSlug() {
        return slug;
    }

    public String getCode() {
        return code;
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

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public List<EvidenceResponse> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectDetailResponse that)) {
            return false;
        }
        return Objects.equals(slug, that.slug)
                && Objects.equals(code, that.code)
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
                && status == that.status
                && contributionType == that.contributionType
                && Objects.equals(evidenceIds, that.evidenceIds)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, code, title, summary, background, responsibilities, solution,
                keyDecisions, technologies, verification, outcome, handoff, status,
                contributionType, evidenceIds, evidence, suggestedQuestions);
    }

    @Override
    public String toString() {
        return "ProjectDetailResponse{" +
                "slug='" + slug + '\'' +
                ", code='" + code + '\'' +
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
                ", evidenceIds=" + evidenceIds +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
