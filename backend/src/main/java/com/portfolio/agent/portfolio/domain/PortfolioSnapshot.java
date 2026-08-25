package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 作品集内容快照：公开发布包 facts 文件反序列化后的顶级载体。
 *
 * <p>由 schemaVersion/contentVersion/publishedAt/owner 加八类领域集合组成：项目、案例、
 * 案例合集、断言、断言—证据关联、预设问题、证据与时间线。加载后必须先通过
 * {@code PortfolioSnapshotValidator} 校验才能进入运行时。questions 字段同时接受旧字段名
 * "questions" 与新字段名 "questionPresets"（{@code @JsonAlias}），timeline 同理兼容
 * "timeline"/"timelineEvents"，以兼容历史发布包。
 *
 * <p>显式不可变类，所有集合在构造时做防御性复制。
 */
public final class PortfolioSnapshot {

    private final String schemaVersion;
    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;
    private final List<CaseStudy> cases;
    private final List<CaseCollection> collections;
    private final List<Claim> claims;
    private final List<ClaimEvidenceLink> claimEvidenceLinks;
    private final List<QuestionDefinition> questions;
    private final List<EvidenceRecord> evidence;
    private final List<TimelineEvent> timeline;

    @JsonCreator
    public PortfolioSnapshot(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("publishedAt") OffsetDateTime publishedAt,
            @JsonProperty("owner") OwnerProfile owner,
            @JsonProperty("projects") List<ProjectProfile> projects,
            @JsonProperty("cases") List<CaseStudy> cases,
            @JsonProperty("collections") List<CaseCollection> collections,
            @JsonProperty("claims") List<Claim> claims,
            @JsonProperty("claimEvidenceLinks") List<ClaimEvidenceLink> claimEvidenceLinks,
            @JsonProperty("questionPresets") @JsonAlias("questions") List<QuestionDefinition> questions,
            @JsonProperty("evidence") List<EvidenceRecord> evidence,
            @JsonProperty("timelineEvents") @JsonAlias("timeline") List<TimelineEvent> timeline
    ) {
        this.schemaVersion = schemaVersion;
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.cases = List.copyOf(cases);
        this.collections = collections == null ? List.of() : List.copyOf(collections);
        this.claims = List.copyOf(claims);
        this.claimEvidenceLinks = List.copyOf(claimEvidenceLinks);
        this.questions = List.copyOf(questions);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OwnerProfile getOwner() {
        return owner;
    }

    public List<ProjectProfile> getProjects() {
        return projects;
    }

    public List<CaseStudy> getCases() {
        return cases;
    }

    public List<CaseCollection> getCollections() {
        return collections;
    }

    public List<Claim> getClaims() {
        return claims;
    }

    public List<ClaimEvidenceLink> getClaimEvidenceLinks() {
        return claimEvidenceLinks;
    }

    public List<QuestionDefinition> getQuestions() {
        return questions;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    /**
     * 以指定发布时间生成内容不变的副本：发布流程写入 publishedAt 时使用，
     * 避免修改已加载的不可变实例。
     */
    public PortfolioSnapshot withPublishedAt(OffsetDateTime value) {
        return new PortfolioSnapshot(
                schemaVersion,
                contentVersion,
                value,
                owner,
                projects,
                cases,
                collections,
                claims,
                claimEvidenceLinks,
                questions,
                evidence,
                timeline
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioSnapshot that)) {
            return false;
        }
        return Objects.equals(schemaVersion, that.schemaVersion)
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(cases, that.cases)
                && Objects.equals(collections, that.collections)
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(questions, that.questions)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, contentVersion, publishedAt, owner, projects, cases,
                collections, claims, claimEvidenceLinks, questions, evidence, timeline);
    }

    @Override
    public String toString() {
        return "PortfolioSnapshot{" +
                "schemaVersion='" + schemaVersion + '\'' +
                ", contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", cases=" + cases +
                ", collections=" + collections +
                ", claims=" + claims +
                ", claimEvidenceLinks=" + claimEvidenceLinks +
                ", questions=" + questions +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                '}';
    }

    /**
     * 兼容构造器：省略案例合集（collections 回退为空列表），供旧调用方过渡使用。
     */
    public PortfolioSnapshot(
            String schemaVersion,
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectProfile> projects,
            List<CaseStudy> cases,
            List<Claim> claims,
            List<ClaimEvidenceLink> claimEvidenceLinks,
            List<QuestionDefinition> questions,
            List<EvidenceRecord> evidence,
            List<TimelineEvent> timeline
    ) {
        this(
                schemaVersion, contentVersion, publishedAt, owner, projects, cases, List.of(),
                claims, claimEvidenceLinks, questions, evidence, timeline
        );
    }
}
