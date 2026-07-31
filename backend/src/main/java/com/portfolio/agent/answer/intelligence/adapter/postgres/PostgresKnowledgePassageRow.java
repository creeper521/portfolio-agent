package com.portfolio.agent.answer.intelligence.adapter.postgres;

import java.util.List;
import java.util.Objects;

public final class PostgresKnowledgePassageRow {

    private final String subjectId;
    private final String claimId;
    private final String content;
    private final List<String> evidenceIds;

    public PostgresKnowledgePassageRow(
            String subjectId,
            String claimId,
            String content,
            List<String> evidenceIds) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.claimId = requireText(claimId, "claimId");
        this.content = requireText(content, "content");
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        if (this.evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds are required");
        }
    }

    public String getSubjectId() { return subjectId; }
    public String getClaimId() { return claimId; }
    public String getContent() { return content; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
