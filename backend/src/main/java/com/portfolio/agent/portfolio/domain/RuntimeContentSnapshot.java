package com.portfolio.agent.portfolio.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class RuntimeContentSnapshot {

    private final String schemaVersion;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final Instant loadedAt;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;
    private final List<Claim> claims;
    private final List<ClaimEvidenceLink> claimEvidenceLinks;
    private final List<QuestionDefinition> questionPresets;
    private final List<EvidenceRecord> approvedEvidence;
    private final List<TimelineEvent> timeline;

    public RuntimeContentSnapshot(
            PortfolioSnapshot content,
            String runtimeBundleHash,
            Instant loadedAt
    ) {
        this.schemaVersion = content.getSchemaVersion();
        this.contentVersion = content.getContentVersion();
        this.runtimeBundleHash = runtimeBundleHash;
        this.loadedAt = loadedAt;
        this.publishedAt = content.getPublishedAt();
        this.owner = content.getOwner();
        this.projects = List.copyOf(content.getProjects());
        this.claims = List.copyOf(content.getClaims());
        this.claimEvidenceLinks = List.copyOf(content.getClaimEvidenceLinks());
        this.questionPresets = List.copyOf(content.getQuestions());
        this.approvedEvidence = List.copyOf(content.getEvidence());
        this.timeline = List.copyOf(content.getTimeline());
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public String getRuntimeBundleHash() {
        return runtimeBundleHash;
    }

    public Instant getLoadedAt() {
        return loadedAt;
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

    public List<Claim> getClaims() {
        return claims;
    }

    public List<ClaimEvidenceLink> getClaimEvidenceLinks() {
        return claimEvidenceLinks;
    }

    public List<QuestionDefinition> getQuestionPresets() {
        return questionPresets;
    }

    public List<QuestionDefinition> getQuestions() {
        return questionPresets;
    }

    public List<EvidenceRecord> getApprovedEvidence() {
        return approvedEvidence;
    }

    public List<EvidenceRecord> getEvidence() {
        return approvedEvidence;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuntimeContentSnapshot that)) {
            return false;
        }
        return Objects.equals(schemaVersion, that.schemaVersion)
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(runtimeBundleHash, that.runtimeBundleHash)
                && Objects.equals(loadedAt, that.loadedAt)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(questionPresets, that.questionPresets)
                && Objects.equals(approvedEvidence, that.approvedEvidence)
                && Objects.equals(timeline, that.timeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, contentVersion, runtimeBundleHash, loadedAt,
                publishedAt, owner, projects, claims, claimEvidenceLinks, questionPresets,
                approvedEvidence, timeline);
    }
}
