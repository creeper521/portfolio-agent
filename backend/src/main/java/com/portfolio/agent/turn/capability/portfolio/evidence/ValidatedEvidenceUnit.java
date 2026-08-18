package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;

import java.util.Objects;

public final class ValidatedEvidenceUnit {
    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicSourceReference sourceReference;
    public ValidatedEvidenceUnit(
            String subjectId, AnswerClaimProjection claim, PublicSourceReference sourceReference) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
    }
    public String getSubjectId() { return subjectId; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicSourceReference getSourceReference() { return sourceReference; }
}
