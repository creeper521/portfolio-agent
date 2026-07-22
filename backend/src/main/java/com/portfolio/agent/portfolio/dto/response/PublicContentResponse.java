package com.portfolio.agent.portfolio.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PublicContentResponse {

    private final String contentVersion;
    private final String runtimeBundleHash;
    private final OffsetDateTime publishedAt;
    private final OwnerResponse owner;
    private final List<ProjectDetailResponse> projects;
    private final List<ClaimResponse> claims;
    private final List<ClaimEvidenceLinkResponse> claimEvidenceLinks;
    private final List<EvidenceResponse> evidence;
    private final List<TimelineEventResponse> timeline;
    private final List<QuestionPresetResponse> questionPresets;

    public PublicContentResponse(
            String contentVersion,
            String runtimeBundleHash,
            OffsetDateTime publishedAt,
            OwnerResponse owner,
            List<ProjectDetailResponse> projects,
            List<ClaimResponse> claims,
            List<ClaimEvidenceLinkResponse> claimEvidenceLinks,
            List<EvidenceResponse> evidence,
            List<TimelineEventResponse> timeline,
            List<QuestionPresetResponse> questionPresets
    ) {
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.claims = List.copyOf(claims);
        this.claimEvidenceLinks = List.copyOf(claimEvidenceLinks);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
        this.questionPresets = List.copyOf(questionPresets);
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getRuntimeBundleHash() {
        return runtimeBundleHash;
    }

    public List<QuestionPresetResponse> getQuestionPresets() {
        return questionPresets;
    }

    public OwnerResponse getOwner() {
        return owner;
    }

    public List<ProjectDetailResponse> getProjects() {
        return projects;
    }

    public List<ClaimResponse> getClaims() { return claims; }

    public List<ClaimEvidenceLinkResponse> getClaimEvidenceLinks() { return claimEvidenceLinks; }

    public List<EvidenceResponse> getEvidence() {
        return evidence;
    }

    public List<TimelineEventResponse> getTimeline() {
        return timeline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicContentResponse that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(runtimeBundleHash, that.runtimeBundleHash)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline)
                && Objects.equals(questionPresets, that.questionPresets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, runtimeBundleHash, publishedAt, owner, projects,
                claims, claimEvidenceLinks, evidence, timeline, questionPresets);
    }

    @Override
    public String toString() {
        return "PublicContentResponse{" +
                "contentVersion='" + contentVersion + '\'' +
                ", runtimeBundleHash='" + runtimeBundleHash + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                ", questionPresets=" + questionPresets +
                '}';
    }
}
