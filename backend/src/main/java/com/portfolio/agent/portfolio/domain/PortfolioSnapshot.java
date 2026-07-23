package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PortfolioSnapshot {

    private final String schemaVersion;
    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;
    private final List<CaseStudy> cases;
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

    public PortfolioSnapshot withPublishedAt(OffsetDateTime value) {
        return new PortfolioSnapshot(
                schemaVersion,
                contentVersion,
                value,
                owner,
                projects,
                cases,
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
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(questions, that.questions)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, contentVersion, publishedAt, owner, projects, cases,
                claims, claimEvidenceLinks, questions, evidence, timeline);
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
                ", claims=" + claims +
                ", claimEvidenceLinks=" + claimEvidenceLinks +
                ", questions=" + questions +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                '}';
    }
}
