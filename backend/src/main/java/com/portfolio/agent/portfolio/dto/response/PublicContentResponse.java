package com.portfolio.agent.portfolio.dto.response;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PublicContentResponse {

    private final String contentVersion;
    private final String runtimeBundleHash;
    private final OffsetDateTime publishedAt;
    private final OwnerResponse owner;
    private final List<CaseCollectionResponse> collections;
    private final List<ProjectDetailResponse> projects;
    private final List<CaseDetailResponse> cases;
    private final List<ClaimResponse> claims;
    private final List<ClaimEvidenceLinkResponse> claimEvidenceLinks;
    private final List<EvidenceResponse> evidence;
    private final List<TimelineEventResponse> timeline;
    private final Map<String, List<String>> caseSlugsByEvidenceId;
    private final List<QuestionPresetResponse> questionPresets;
    private final AgentAvailabilityResponse agentAvailability;

    public PublicContentResponse(
            String contentVersion,
            String runtimeBundleHash,
            OffsetDateTime publishedAt,
            OwnerResponse owner,
            List<CaseCollectionResponse> collections,
            List<ProjectDetailResponse> projects,
            List<CaseDetailResponse> cases,
            List<ClaimResponse> claims,
            List<ClaimEvidenceLinkResponse> claimEvidenceLinks,
            List<EvidenceResponse> evidence,
            List<TimelineEventResponse> timeline,
            Map<String, List<String>> caseSlugsByEvidenceId,
            List<QuestionPresetResponse> questionPresets,
            AgentAvailabilityResponse agentAvailability
    ) {
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.collections = List.copyOf(collections);
        this.projects = List.copyOf(projects);
        this.cases = List.copyOf(cases);
        this.claims = List.copyOf(claims);
        this.claimEvidenceLinks = List.copyOf(claimEvidenceLinks);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
        LinkedHashMap<String, List<String>> copiedCaseSlugs = new LinkedHashMap<>();
        caseSlugsByEvidenceId.forEach((evidenceId, caseSlugs) ->
                copiedCaseSlugs.put(evidenceId, List.copyOf(caseSlugs)));
        this.caseSlugsByEvidenceId = Map.copyOf(copiedCaseSlugs);
        this.questionPresets = List.copyOf(questionPresets);
        this.agentAvailability = Objects.requireNonNull(agentAvailability, "agentAvailability");
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

    public AgentAvailabilityResponse getAgentAvailability() {
        return agentAvailability;
    }

    public OwnerResponse getOwner() {
        return owner;
    }

    public List<CaseCollectionResponse> getCollections() {
        return collections;
    }

    public List<ProjectDetailResponse> getProjects() {
        return projects;
    }

    public List<CaseDetailResponse> getCases() {
        return cases;
    }

    public List<ClaimResponse> getClaims() { return claims; }

    public List<ClaimEvidenceLinkResponse> getClaimEvidenceLinks() { return claimEvidenceLinks; }

    public List<EvidenceResponse> getEvidence() {
        return evidence;
    }

    public List<TimelineEventResponse> getTimeline() {
        return timeline;
    }

    public Map<String, List<String>> getCaseSlugsByEvidenceId() {
        return caseSlugsByEvidenceId;
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
                && Objects.equals(collections, that.collections)
                && Objects.equals(projects, that.projects)
                && Objects.equals(cases, that.cases)
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline)
                && Objects.equals(caseSlugsByEvidenceId, that.caseSlugsByEvidenceId)
                && Objects.equals(questionPresets, that.questionPresets)
                && Objects.equals(agentAvailability, that.agentAvailability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, runtimeBundleHash, publishedAt, owner, collections,
                projects, cases,
                claims, claimEvidenceLinks, evidence, timeline, caseSlugsByEvidenceId,
                questionPresets, agentAvailability);
    }

    @Override
    public String toString() {
        return "PublicContentResponse{" +
                "contentVersion='" + contentVersion + '\'' +
                ", runtimeBundleHash='" + runtimeBundleHash + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", collections=" + collections +
                ", projects=" + projects +
                ", cases=" + cases +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                ", caseSlugsByEvidenceId=" + caseSlugsByEvidenceId +
                ", questionPresets=" + questionPresets +
                ", agentAvailability=" + agentAvailability +
                '}';
    }
}
