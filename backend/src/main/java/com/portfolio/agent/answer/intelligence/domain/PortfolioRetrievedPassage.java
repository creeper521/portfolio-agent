package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievedPassage {

    private final String passageId;
    private final String subjectId;
    private final String claimId;
    private final String content;
    private final List<String> evidenceIds;

    public PortfolioRetrievedPassage(
            String passageId,
            String subjectId,
            String claimId,
            String content,
            List<String> evidenceIds) {
        this.passageId = requireText(passageId, "passageId");
        this.subjectId = requireText(subjectId, "subjectId");
        this.claimId = requireText(claimId, "claimId");
        this.content = requireText(content, "content");
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        if (this.evidenceIds.isEmpty()) { throw new IllegalArgumentException("evidenceIds are required"); }
    }

    public String getPassageId() { return passageId; }
    public String getId() { return passageId; }
    public String getSubjectId() { return subjectId; }
    public String getClaimId() { return claimId; }
    public String getContent() { return content; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievedPassage that)) { return false; }
        return Objects.equals(passageId, that.passageId)
                && Objects.equals(subjectId, that.subjectId)
                && Objects.equals(claimId, that.claimId)
                && Objects.equals(content, that.content)
                && Objects.equals(evidenceIds, that.evidenceIds);
    }

    @Override
    public int hashCode() { return Objects.hash(passageId, subjectId, claimId, content, evidenceIds); }

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
