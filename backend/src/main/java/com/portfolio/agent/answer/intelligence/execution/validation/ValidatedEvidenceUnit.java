package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;

import java.util.Objects;

/** Immutable promoted atomic unit. */
public final class ValidatedEvidenceUnit {
    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicSourceReference sourceReference;

    public ValidatedEvidenceUnit(
            String subjectId, AnswerClaimProjection claim, PublicSourceReference sourceReference) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.sourceReference = Objects.requireNonNull(sourceReference, "sourceReference");
    }

    public String getSubjectId() { return subjectId; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicSourceReference getSourceReference() { return sourceReference; }
    public String getClaimId() { return claim.getId(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ValidatedEvidenceUnit that)) return false;
        return subjectId.equals(that.subjectId) && claim.equals(that.claim)
                && sourceReference.equals(that.sourceReference);
    }
    @Override public int hashCode() { return Objects.hash(subjectId, claim, sourceReference); }
    @Override public String toString() { return "ValidatedEvidenceUnit{hasClaim=true, hasSourceReference=true}"; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
