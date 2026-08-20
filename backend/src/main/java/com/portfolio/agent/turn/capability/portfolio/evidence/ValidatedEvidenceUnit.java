package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;

import java.util.Objects;

public final class ValidatedEvidenceUnit {
    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicSourceReferenceValue sourceReference;
    public ValidatedEvidenceUnit(
            String subjectId, AnswerClaimProjection claim, PublicSourceReferenceValue sourceReference) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
    }
    public String getSubjectId() { return subjectId; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicSourceReferenceValue getSourceReference() { return sourceReference; }
}
