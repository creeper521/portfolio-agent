package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class QuestionDefinition {

    private final String id;
    private final String text;
    private final List<String> aliases;
    private final List<String> audiences;
    private final List<String> projectIds;
    private final List<String> caseIds;
    private final List<String> topics;
    private final List<ClaimCategory> preferredClaimCategories;
    private final List<String> placements;
    private final boolean deterministicEntry;
    private final int displayOrder;
    private final String contractSubjectId;
    private final List<String> requiredClaimIds;
    private final List<String> supportingClaimIds;
    private final QuestionEvidenceRequirement evidenceRequirement;
    private final PresetContractStatus contractStatus;

    @JsonCreator
    public QuestionDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("aliases") List<String> aliases,
            @JsonProperty("audiences") List<String> audiences,
            @JsonProperty("projectIds") List<String> projectIds,
            @JsonProperty("caseIds") List<String> caseIds,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("preferredClaimCategories") List<ClaimCategory> preferredClaimCategories,
            @JsonProperty("placements") List<String> placements,
            @JsonProperty("deterministicEntry") boolean deterministicEntry,
            @JsonProperty("displayOrder") int displayOrder,
            @JsonProperty("contractSubjectId") String contractSubjectId,
            @JsonProperty("requiredClaimIds") List<String> requiredClaimIds,
            @JsonProperty("supportingClaimIds") List<String> supportingClaimIds,
            @JsonProperty("evidenceRequirement") QuestionEvidenceRequirement evidenceRequirement,
            @JsonProperty("contractStatus") PresetContractStatus contractStatus
    ) {
        this.id = id;
        this.text = text;
        this.aliases = List.copyOf(aliases);
        this.audiences = List.copyOf(audiences);
        this.projectIds = List.copyOf(projectIds);
        this.caseIds = List.copyOf(caseIds);
        this.topics = List.copyOf(topics);
        this.preferredClaimCategories = List.copyOf(preferredClaimCategories);
        this.placements = List.copyOf(placements);
        this.deterministicEntry = deterministicEntry;
        this.displayOrder = displayOrder;
        this.contractSubjectId = contractSubjectId == null ? null : contractSubjectId.trim();
        this.requiredClaimIds = immutableOrEmpty(requiredClaimIds);
        this.supportingClaimIds = immutableOrEmpty(supportingClaimIds);
        this.evidenceRequirement = evidenceRequirement == null
                ? new QuestionEvidenceRequirement(1, true)
                : evidenceRequirement;
        this.contractStatus = contractStatus == null ? PresetContractStatus.DRAFT : contractStatus;
    }

    public QuestionDefinition(
            String id,
            String text,
            List<String> aliases,
            List<String> audiences,
            List<String> projectIds,
            List<String> caseIds,
            List<String> topics,
            List<ClaimCategory> preferredClaimCategories,
            List<String> placements,
            boolean deterministicEntry,
            int displayOrder
    ) {
        this(id, text, aliases, audiences, projectIds, caseIds, topics, preferredClaimCategories,
                placements, deterministicEntry, displayOrder, null, List.of(), List.of(),
                new QuestionEvidenceRequirement(1, true), PresetContractStatus.DRAFT);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public List<String> getProjectIds() { return projectIds; }

    public List<String> getCaseIds() { return caseIds; }

    public List<String> getTopics() { return topics; }

    public List<ClaimCategory> getPreferredClaimCategories() { return preferredClaimCategories; }

    public List<String> getPlacements() {
        return placements;
    }

    public boolean isDeterministicEntry() { return deterministicEntry; }

    public int getDisplayOrder() { return displayOrder; }

    public String getContractSubjectId() { return contractSubjectId; }

    public List<String> getRequiredClaimIds() { return requiredClaimIds; }

    public List<String> getSupportingClaimIds() { return supportingClaimIds; }

    public QuestionEvidenceRequirement getEvidenceRequirement() { return evidenceRequirement; }

    public PresetContractStatus getContractStatus() { return contractStatus; }

    @JsonIgnore
    public boolean isActiveContract() { return contractStatus == PresetContractStatus.ACTIVE; }

    @JsonIgnore
    public String getContractVersion() {
        if (contractStatus != PresetContractStatus.ACTIVE) {
            return null;
        }
        return PresetContractVersion.calculate(id, text, aliases, contractSubjectId,
                requiredClaimIds, supportingClaimIds, evidenceRequirement, contractStatus);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(text, that.text)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(audiences, that.audiences)
                && Objects.equals(projectIds, that.projectIds)
                && Objects.equals(caseIds, that.caseIds)
                && Objects.equals(topics, that.topics)
                && Objects.equals(preferredClaimCategories, that.preferredClaimCategories)
                && Objects.equals(placements, that.placements)
                && deterministicEntry == that.deterministicEntry
                && displayOrder == that.displayOrder
                && Objects.equals(contractSubjectId, that.contractSubjectId)
                && Objects.equals(requiredClaimIds, that.requiredClaimIds)
                && Objects.equals(supportingClaimIds, that.supportingClaimIds)
                && Objects.equals(evidenceRequirement, that.evidenceRequirement)
                && contractStatus == that.contractStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, aliases, audiences, projectIds, caseIds, topics,
                preferredClaimCategories, placements, deterministicEntry, displayOrder,
                contractSubjectId, requiredClaimIds, supportingClaimIds, evidenceRequirement,
                contractStatus);
    }

    @Override
    public String toString() {
        return "QuestionDefinition{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", aliases=" + aliases +
                ", projectIds=" + projectIds +
                ", caseIds=" + caseIds +
                '}';
    }

    private static <T> List<T> immutableOrEmpty(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
