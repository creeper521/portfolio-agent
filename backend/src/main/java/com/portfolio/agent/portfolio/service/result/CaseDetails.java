package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;

import java.util.List;
import java.util.Objects;

/**
 * 单个案例的公开详情聚合。
 *
 * <p>由 PortfolioService 组装：案例本体、过滤后的证据（仅 APPROVED 且不公开原始内容）、
 * 建议问题文本、所属项目 slug（独立案例为 null）与所属合集 slug 列表。
 * 列表字段在构造时做防御性拷贝，实例不可变。
 */
public final class CaseDetails {

    private final CaseStudy caseStudy;
    private final List<EvidenceRecord> evidence;
    private final List<String> suggestedQuestions;
    private final String projectSlug;
    private final List<String> collectionSlugs;

    public CaseDetails(
            CaseStudy caseStudy,
            List<EvidenceRecord> evidence,
            List<String> suggestedQuestions,
            String projectSlug,
            List<String> collectionSlugs
    ) {
        this.caseStudy = caseStudy;
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
        this.projectSlug = projectSlug;
        this.collectionSlugs = List.copyOf(collectionSlugs);
    }

    public CaseStudy getCaseStudy() {
        return caseStudy;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

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
        if (!(other instanceof CaseDetails that)) {
            return false;
        }
        return Objects.equals(caseStudy, that.caseStudy)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions)
                && Objects.equals(projectSlug, that.projectSlug)
                && Objects.equals(collectionSlugs, that.collectionSlugs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseStudy, evidence, suggestedQuestions, projectSlug, collectionSlugs);
    }

    @Override
    public String toString() {
        return "CaseDetails{" +
                "caseStudy=" + caseStudy +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                ", projectSlug='" + projectSlug + '\'' +
                ", collectionSlugs=" + collectionSlugs +
                '}';
    }

    public CaseDetails(
            CaseStudy caseStudy,
            List<EvidenceRecord> evidence,
            List<String> suggestedQuestions,
            String projectSlug
    ) {
        this(caseStudy, evidence, suggestedQuestions, projectSlug, List.of());
    }
}
