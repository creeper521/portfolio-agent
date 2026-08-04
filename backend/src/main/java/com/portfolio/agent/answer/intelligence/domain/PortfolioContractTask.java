package com.portfolio.agent.answer.intelligence.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioContractTask {

    private final String presetId;
    private final String contractVersion;
    private final String canonicalQuestion;
    private final String subjectId;
    private final List<String> requiredClaimIds;
    private final List<String> supportingClaimIds;
    private final int minimumApprovedEvidencePerRequiredClaim;

    public PortfolioContractTask(
            String presetId,
            String contractVersion,
            String canonicalQuestion,
            String subjectId,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds,
            int minimumApprovedEvidencePerRequiredClaim
    ) {
        this.presetId = requireText(presetId, "presetId");
        this.contractVersion = requireText(contractVersion, "contractVersion");
        this.canonicalQuestion = requireText(canonicalQuestion, "canonicalQuestion");
        this.subjectId = requireText(subjectId, "subjectId");
        this.requiredClaimIds = immutableUnique(requiredClaimIds, "requiredClaimIds", true);
        this.supportingClaimIds = immutableUnique(supportingClaimIds, "supportingClaimIds", false);
        Set<String> overlap = new LinkedHashSet<>(this.requiredClaimIds);
        overlap.retainAll(this.supportingClaimIds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("requiredClaimIds and supportingClaimIds overlap");
        }
        if (minimumApprovedEvidencePerRequiredClaim < 1) {
            throw new IllegalArgumentException(
                    "minimumApprovedEvidencePerRequiredClaim must be at least 1");
        }
        this.minimumApprovedEvidencePerRequiredClaim = minimumApprovedEvidencePerRequiredClaim;
    }

    public String getPresetId() { return presetId; }
    public String getContractVersion() { return contractVersion; }
    public String getCanonicalQuestion() { return canonicalQuestion; }
    public String getSubjectId() { return subjectId; }
    public List<String> getRequiredClaimIds() { return requiredClaimIds; }
    public List<String> getSupportingClaimIds() { return supportingClaimIds; }
    public int getMinimumApprovedEvidencePerRequiredClaim() {
        return minimumApprovedEvidencePerRequiredClaim;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static List<String> immutableUnique(
            List<String> values,
            String name,
            boolean required
    ) {
        Objects.requireNonNull(values, name);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            unique.add(requireText(value, name));
        }
        if (unique.size() != values.size()) {
            throw new IllegalArgumentException(name + " must be unique");
        }
        if (required && unique.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return List.copyOf(unique);
    }
}
