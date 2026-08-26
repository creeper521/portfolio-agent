/**
 * 案例摘要的公开响应载体。
 *
 * <p>用于项目详情页的案例列表，不含叙述细节与证据。
 */
package com.portfolio.agent.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
import com.portfolio.agent.portfolio.domain.ContributionType;

import java.util.Objects;
import java.util.List;

public final class CaseSummaryResponse {

    private final String slug;
    private final String code;
    private final CaseType type;
    private final String title;
    private final String summary;
    private final AchievementStatus achievementStatus;
    private final ContributionType contributionType;
    private final String projectSlug;
    private final List<String> collectionSlugs;

    public CaseSummaryResponse(
            String slug,
            String code,
            CaseType type,
            String title,
            String summary,
            AchievementStatus achievementStatus,
            ContributionType contributionType,
            String projectSlug,
            List<String> collectionSlugs
    ) {
        this.slug = slug;
        this.code = code;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.projectSlug = projectSlug;
        this.collectionSlugs = List.copyOf(collectionSlugs);
    }

    public static CaseSummaryResponse from(
            CaseStudy caseStudy,
            String projectSlug,
            List<String> collectionSlugs
    ) {
        return new CaseSummaryResponse(
                caseStudy.getSlug(),
                caseStudy.getCode(),
                caseStudy.getType(),
                caseStudy.getTitle(),
                caseStudy.getSummary(),
                caseStudy.getAchievementStatus(),
                caseStudy.getContributionType(),
                projectSlug,
                collectionSlugs
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

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getProjectSlug() {
        return projectSlug;
    }

    public List<String> getCollectionSlugs() {
        return collectionSlugs;
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
                && contributionType == that.contributionType
                && Objects.equals(projectSlug, that.projectSlug)
                && Objects.equals(collectionSlugs, that.collectionSlugs);
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
                projectSlug,
                collectionSlugs
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
                ", projectSlug='" + projectSlug + '\'' +
                ", collectionSlugs=" + collectionSlugs +
                '}';
    }
}
