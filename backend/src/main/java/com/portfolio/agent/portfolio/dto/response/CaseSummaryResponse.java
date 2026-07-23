package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
import com.portfolio.agent.portfolio.domain.ContributionType;

import java.util.Objects;

public final class CaseSummaryResponse {

    private final String slug;
    private final String code;
    private final CaseType type;
    private final String title;
    private final String summary;
    private final AchievementStatus achievementStatus;
    private final ContributionType contributionType;

    public CaseSummaryResponse(
            String slug,
            String code,
            CaseType type,
            String title,
            String summary,
            AchievementStatus achievementStatus,
            ContributionType contributionType
    ) {
        this.slug = slug;
        this.code = code;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
    }

    public static CaseSummaryResponse from(CaseStudy caseStudy) {
        return new CaseSummaryResponse(
                caseStudy.getSlug(),
                caseStudy.getCode(),
                caseStudy.getType(),
                caseStudy.getTitle(),
                caseStudy.getSummary(),
                caseStudy.getAchievementStatus(),
                caseStudy.getContributionType()
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CaseSummaryResponse that)) {
            return false;
        }
        return Objects.equals(slug, that.slug)
                && Objects.equals(code, that.code)
                && type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType;
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
                contributionType
        );
    }

    @Override
    public String toString() {
        return "CaseSummaryResponse{" +
                "slug='" + slug + '\'' +
                ", code='" + code + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", achievementStatus=" + achievementStatus +
                ", contributionType=" + contributionType +
                '}';
    }
}
