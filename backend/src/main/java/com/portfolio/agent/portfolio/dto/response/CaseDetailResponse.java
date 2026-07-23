package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
import com.portfolio.agent.portfolio.domain.ContributionType;

import java.util.List;
import java.util.Objects;

public final class CaseDetailResponse {

    private final String slug;
    private final String code;
    private final CaseType type;
    private final String title;
    private final String summary;
    private final AchievementStatus achievementStatus;
    private final ContributionType contributionType;
    private final String problem;
    private final List<String> actions;
    private final List<String> decisions;
    private final List<String> verification;
    private final String outcome;
    private final List<String> limitations;
    private final String projectSlug;
    private final List<EvidenceResponse> evidence;
    private final List<String> suggestedQuestions;

    public CaseDetailResponse(
            String slug,
            String code,
            CaseType type,
            String title,
            String summary,
            AchievementStatus achievementStatus,
            ContributionType contributionType,
            String problem,
            List<String> actions,
            List<String> decisions,
            List<String> verification,
            String outcome,
            List<String> limitations,
            String projectSlug,
            List<EvidenceResponse> evidence,
            List<String> suggestedQuestions
    ) {
        this.slug = slug;
        this.code = code;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.problem = problem;
        this.actions = List.copyOf(actions);
        this.decisions = List.copyOf(decisions);
        this.verification = List.copyOf(verification);
        this.outcome = outcome;
        this.limitations = List.copyOf(limitations);
        this.projectSlug = projectSlug;
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public static CaseDetailResponse from(
            CaseStudy caseStudy,
            String projectSlug,
            List<EvidenceResponse> evidence,
            List<String> suggestedQuestions
    ) {
        return new CaseDetailResponse(
                caseStudy.getSlug(),
                caseStudy.getCode(),
                caseStudy.getType(),
                caseStudy.getTitle(),
                caseStudy.getSummary(),
                caseStudy.getAchievementStatus(),
                caseStudy.getContributionType(),
                caseStudy.getProblem(),
                caseStudy.getActions(),
                caseStudy.getDecisions(),
                caseStudy.getVerification(),
                caseStudy.getOutcome(),
                caseStudy.getLimitations(),
                projectSlug,
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

    public CaseType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public AchievementStatus getAchievementStatus() {
        return achievementStatus;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }

    public String getProblem() {
        return problem;
    }

    public List<String> getActions() {
        return actions;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public List<String> getVerification() {
        return verification;
    }

    public String getOutcome() {
        return outcome;
    }

    public List<String> getLimitations() {
        return limitations;
    }

    public String getProjectSlug() {
        return projectSlug;
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
        if (!(other instanceof CaseDetailResponse that)) {
            return false;
        }
        return Objects.equals(slug, that.slug)
                && Objects.equals(code, that.code)
                && type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType
                && Objects.equals(problem, that.problem)
                && Objects.equals(actions, that.actions)
                && Objects.equals(decisions, that.decisions)
                && Objects.equals(verification, that.verification)
                && Objects.equals(outcome, that.outcome)
                && Objects.equals(limitations, that.limitations)
                && Objects.equals(projectSlug, that.projectSlug)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                slug,
                code,
                type,
                title,
                summary,
                achievementStatus,
                contributionType,
                problem,
                actions,
                decisions,
                verification,
                outcome,
                limitations,
                projectSlug,
                evidence,
                suggestedQuestions
        );
    }

    @Override
    public String toString() {
        return "CaseDetailResponse{" +
                "slug='" + slug + '\'' +
                ", code='" + code + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", achievementStatus=" + achievementStatus +
                ", contributionType=" + contributionType +
                ", problem='" + problem + '\'' +
                ", actions=" + actions +
                ", decisions=" + decisions +
                ", verification=" + verification +
                ", outcome='" + outcome + '\'' +
                ", limitations=" + limitations +
                ", projectSlug='" + projectSlug + '\'' +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
