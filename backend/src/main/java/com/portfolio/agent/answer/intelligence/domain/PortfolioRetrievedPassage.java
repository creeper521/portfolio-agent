package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioRetrievedPassage {

    private final String passageId;
    private final String subjectId;
    private final String content;
    private final AnswerClaimProjection claim;
    private final List<PortfolioRetrievedEvidenceReference> evidenceReferences;

    public PortfolioRetrievedPassage(
            String passageId,
            String subjectId,
            String retrievalContent,
            AnswerClaimProjection claim,
            List<PortfolioRetrievedEvidenceReference> evidenceReferences) {
        this.passageId = requireText(passageId, "passageId");
        this.subjectId = requireText(subjectId, "subjectId");
        this.content = requireText(retrievalContent, "retrievalContent");
        List<PortfolioRetrievedEvidenceReference> references = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        if (references.isEmpty()) {
            throw new IllegalArgumentException("evidenceReferences are required");
        }
        this.claim = validateClaim(claim, references);
        this.evidenceReferences = references;
    }

    private static AnswerClaimProjection validateClaim(
            AnswerClaimProjection claim,
            List<PortfolioRetrievedEvidenceReference> evidenceReferences) {
        Objects.requireNonNull(claim, "claim");
        requireNonBlank(claim.getId(), "claim id");
        if (claim.getVerificationStatus() != AnswerClaimVerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("claim must be VERIFIED");
        }
        requireNonBlank(claim.getCategory() == null ? null : claim.getCategory().name(),
                "claim category");
        requireNonBlank(claim.getStatement(), "claim statement");
        requireNonBlank(claim.getDetail(), "claim detail");
        requireNonBlank(claim.getAchievementStatus() == null ? null
                : claim.getAchievementStatus().name(), "claim achievement status");
        requireNonBlank(claim.getContributionType() == null ? null
                : claim.getContributionType().name(), "claim contribution type");
        requireNonBlank(claim.getVerificationBasis() == null ? null
                : claim.getVerificationBasis().name(), "claim verification basis");
        requireNonBlank(claim.getMateriality() == null ? null
                : claim.getMateriality().name(), "claim materiality");
        Set<String> referenceIds = new HashSet<>();
        for (PortfolioRetrievedEvidenceReference reference : evidenceReferences) {
            referenceIds.add(reference.getEvidenceId());
            if (!"APPROVED".equals(reference.getPublicStatus())) {
                throw new IllegalArgumentException("evidence reference must be APPROVED");
            }
        }
        if (!new HashSet<>(claim.getDirectEvidenceIds()).equals(referenceIds)) {
            throw new IllegalArgumentException(
                    "claim direct evidence ids must match passage evidence references");
        }
        return claim;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    public String getPassageId() { return passageId; }
    public String getId() { return passageId; }
    public String getSubjectId() { return subjectId; }
    public String getClaimId() { return claim.getId(); }
    public String getContent() { return content; }
    public AnswerClaimProjection getClaim() { return claim; }
    public List<PortfolioRetrievedEvidenceReference> getEvidenceReferences() {
        return evidenceReferences;
    }
    public List<String> getEvidenceIds() {
        return evidenceReferences.stream()
                .map(PortfolioRetrievedEvidenceReference::getEvidenceId)
                .toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievedPassage that)) { return false; }
        return Objects.equals(passageId, that.passageId)
                && Objects.equals(subjectId, that.subjectId)
                && Objects.equals(content, that.content)
                && Objects.equals(claim, that.claim)
                && Objects.equals(evidenceReferences, that.evidenceReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passageId, subjectId, content, claim, evidenceReferences);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievedPassage{" + "passageId='" + passageId + '\''
                + ", subjectId='" + subjectId + '\'' + ", claimId='" + getClaimId() + '\''
                + '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(fieldName + " is required"); }
        return value.trim();
    }
}
