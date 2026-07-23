package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.TimelineEvent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PublicContent {

    private final String contentVersion;
    private final String runtimeBundleHash;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectDetails> projects;
    private final List<CaseDetails> cases;
    private final List<Claim> claims;
    private final List<ClaimEvidenceLink> claimEvidenceLinks;
    private final List<EvidenceRecord> evidence;
    private final List<TimelineEvent> timeline;
    private final Map<String, List<String>> projectSlugsByEvidenceId;
    private final Map<String, List<String>> caseSlugsByEvidenceId;
    private final Map<String, List<String>> claimIdsByEvidenceId;
    private final List<QuestionDefinition> questionPresets;

    public PublicContent(
            String contentVersion,
            String runtimeBundleHash,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectDetails> projects,
            List<CaseDetails> cases,
            List<Claim> claims,
            List<ClaimEvidenceLink> claimEvidenceLinks,
            List<EvidenceRecord> evidence,
            List<TimelineEvent> timeline,
            Map<String, List<String>> projectSlugsByEvidenceId,
            Map<String, List<String>> caseSlugsByEvidenceId,
            Map<String, List<String>> claimIdsByEvidenceId,
            List<QuestionDefinition> questionPresets
    ) {
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.cases = List.copyOf(cases);
        this.claims = List.copyOf(claims);
        this.claimEvidenceLinks = List.copyOf(claimEvidenceLinks);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
        LinkedHashMap<String, List<String>> copiedProjectSlugs = new LinkedHashMap<>();
        projectSlugsByEvidenceId.forEach(
                (evidenceId, projectSlugs) ->
                        copiedProjectSlugs.put(evidenceId, List.copyOf(projectSlugs))
        );
        this.projectSlugsByEvidenceId = Map.copyOf(copiedProjectSlugs);
        LinkedHashMap<String, List<String>> copiedCaseSlugs = new LinkedHashMap<>();
        caseSlugsByEvidenceId.forEach(
                (evidenceId, caseSlugs) ->
                        copiedCaseSlugs.put(evidenceId, List.copyOf(caseSlugs))
        );
        this.caseSlugsByEvidenceId = Map.copyOf(copiedCaseSlugs);
        LinkedHashMap<String, List<String>> copiedClaimIds = new LinkedHashMap<>();
        claimIdsByEvidenceId.forEach((evidenceId, claimIds) ->
                copiedClaimIds.put(evidenceId, List.copyOf(claimIds)));
        this.claimIdsByEvidenceId = Map.copyOf(copiedClaimIds);
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

    public List<QuestionDefinition> getQuestionPresets() {
        return questionPresets;
    }

    public List<Claim> getClaims() { return claims; }

    public List<ClaimEvidenceLink> getClaimEvidenceLinks() { return claimEvidenceLinks; }

    public Map<String, List<String>> getClaimIdsByEvidenceId() { return claimIdsByEvidenceId; }

    public OwnerProfile getOwner() {
        return owner;
    }

    public List<ProjectDetails> getProjects() {
        return projects;
    }

    public List<CaseDetails> getCases() {
        return cases;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    public Map<String, List<String>> getProjectSlugsByEvidenceId() {
        return projectSlugsByEvidenceId;
    }

    public Map<String, List<String>> getCaseSlugsByEvidenceId() {
        return caseSlugsByEvidenceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicContent that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(runtimeBundleHash, that.runtimeBundleHash)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(cases, that.cases)
                && Objects.equals(claims, that.claims)
                && Objects.equals(claimEvidenceLinks, that.claimEvidenceLinks)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline)
                && Objects.equals(projectSlugsByEvidenceId, that.projectSlugsByEvidenceId)
                && Objects.equals(caseSlugsByEvidenceId, that.caseSlugsByEvidenceId)
                && Objects.equals(claimIdsByEvidenceId, that.claimIdsByEvidenceId)
                && Objects.equals(questionPresets, that.questionPresets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, runtimeBundleHash, publishedAt, owner, projects, cases,
                claims, claimEvidenceLinks, evidence, timeline, projectSlugsByEvidenceId,
                caseSlugsByEvidenceId, claimIdsByEvidenceId, questionPresets);
    }

    @Override
    public String toString() {
        return "PublicContent{" +
                "contentVersion='" + contentVersion + '\'' +
                ", runtimeBundleHash='" + runtimeBundleHash + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", cases=" + cases +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                ", projectSlugsByEvidenceId=" + projectSlugsByEvidenceId +
                ", caseSlugsByEvidenceId=" + caseSlugsByEvidenceId +
                ", questionPresets=" + questionPresets +
                '}';
    }
}
