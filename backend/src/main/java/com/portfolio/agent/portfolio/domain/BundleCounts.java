package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * 发布包条目计数：manifest 中记录的各领域对象数量。
 *
 * <p>加载发布包时用 {@link #matches(PortfolioSnapshot)} 将计数与快照实际集合大小
 * 逐一核对，防止清单与内容不一致。cases 使用包装类型 Integer 以兼容尚未包含
 * 案例计数的旧版 manifest（缺失时按 0 处理，负数则拒绝）。
 */
public final class BundleCounts {
    private final int projects;
    private final int cases;
    private final int claims;
    private final int evidence;
    private final int claimEvidenceLinks;
    private final int timelineEvents;
    private final int questionPresets;

    @JsonCreator
    public BundleCounts(@JsonProperty("projects") int projects,
            @JsonProperty("cases") Integer cases,
            @JsonProperty("claims") int claims,
            @JsonProperty("evidence") int evidence,
            @JsonProperty("claimEvidenceLinks") int claimEvidenceLinks,
            @JsonProperty("timelineEvents") int timelineEvents,
            @JsonProperty("questionPresets") int questionPresets) {
        if (cases != null && cases < 0) {
            throw new IllegalArgumentException("cases must not be negative");
        }
        this.projects = projects;
        this.cases = cases == null ? 0 : cases;
        this.claims = claims;
        this.evidence = evidence;
        this.claimEvidenceLinks = claimEvidenceLinks;
        this.timelineEvents = timelineEvents;
        this.questionPresets = questionPresets;
    }
    public int getProjects() { return projects; }
    public int getCases() { return cases; }
    public int getClaims() { return claims; }
    public int getEvidence() { return evidence; }
    public int getClaimEvidenceLinks() { return claimEvidenceLinks; }
    public int getTimelineEvents() { return timelineEvents; }
    public int getQuestionPresets() { return questionPresets; }
    /**
     * 核对计数是否与快照中各集合的实际大小完全一致（全部相等才返回 true）。
     */
    public boolean matches(PortfolioSnapshot value) {
        return projects == value.getProjects().size() && cases == value.getCases().size()
                && claims == value.getClaims().size()
                && evidence == value.getEvidence().size()
                && claimEvidenceLinks == value.getClaimEvidenceLinks().size()
                && timelineEvents == value.getTimeline().size()
                && questionPresets == value.getQuestions().size();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BundleCounts that)) return false;
        return projects == that.projects && cases == that.cases && claims == that.claims
                && evidence == that.evidence
                && claimEvidenceLinks == that.claimEvidenceLinks
                && timelineEvents == that.timelineEvents && questionPresets == that.questionPresets;
    }
    @Override public int hashCode() {
        return Objects.hash(projects, cases, claims, evidence, claimEvidenceLinks,
                timelineEvents, questionPresets);
    }
}
