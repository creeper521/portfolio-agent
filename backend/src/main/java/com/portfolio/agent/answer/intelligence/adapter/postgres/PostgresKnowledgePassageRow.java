package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.selection.domain.EvidenceReference;
import java.util.List;
import java.util.Objects;

public final class PostgresKnowledgePassageRow {

    private final String subjectId;
    private final String claimId;
    private final String content;
    private final List<EvidenceReference> evidenceReferences;

    public PostgresKnowledgePassageRow(
            String subjectId,
            String claimId,
            String content,
            List<EvidenceReference> evidenceReferences) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.claimId = requireText(claimId, "claimId");
        this.content = requireText(content, "content");
        this.evidenceReferences = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        if (this.evidenceReferences.isEmpty()) {
            throw new IllegalArgumentException("evidenceReferences are required");
        }
    }

    public String getSubjectId() { return subjectId; }
    public String getClaimId() { return claimId; }
    public String getContent() { return content; }
    public List<EvidenceReference> getEvidenceReferences() { return evidenceReferences; }
    public List<String> getEvidenceIds() {
        return evidenceReferences.stream().map(EvidenceReference::getEvidenceId).toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
