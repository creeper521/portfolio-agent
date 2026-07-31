package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievedPassage {

    private final String passageId;
    private final String subjectId;
    private final String claimId;
    private final String content;
    private final List<PortfolioRetrievedEvidenceReference> evidenceReferences;

    public PortfolioRetrievedPassage(
            String passageId,
            String subjectId,
            String claimId,
            String content,
            List<PortfolioRetrievedEvidenceReference> evidenceReferences) {
        this.passageId = requireText(passageId, "passageId");
        this.subjectId = requireText(subjectId, "subjectId");
        this.claimId = requireText(claimId, "claimId");
        this.content = requireText(content, "content");
        this.evidenceReferences = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        if (this.evidenceReferences.isEmpty()) {
            throw new IllegalArgumentException("evidenceReferences are required");
        }
    }

    public String getPassageId() { return passageId; }
    public String getId() { return passageId; }
    public String getSubjectId() { return subjectId; }
    public String getClaimId() { return claimId; }
    public String getContent() { return content; }
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
                && Objects.equals(claimId, that.claimId)
                && Objects.equals(content, that.content)
                && Objects.equals(evidenceReferences, that.evidenceReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passageId, subjectId, claimId, content, evidenceReferences);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievedPassage{" + "passageId='" + passageId + '\''
                + ", subjectId='" + subjectId + '\'' + ", claimId='" + claimId + '\'' + '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(fieldName + " is required"); }
        return value.trim();
    }
}
