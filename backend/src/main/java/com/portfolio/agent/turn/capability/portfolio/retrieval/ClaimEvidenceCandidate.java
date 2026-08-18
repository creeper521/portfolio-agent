package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;

import java.util.Objects;

/** Atomic candidate: a verified claim and its complete approved evidence descriptor. */
public final class ClaimEvidenceCandidate {

    private final String subjectId;
    private final AnswerClaimProjection claim;
    private final PublicEvidenceDescriptor evidence;
    private final String retrievalTarget;

    public ClaimEvidenceCandidate(
            String subjectId, AnswerClaimProjection claim,
            PublicEvidenceDescriptor evidence, String retrievalTarget) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.retrievalTarget = requireText(retrievalTarget, "retrievalTarget");
        if (claim.getId() == null || claim.getId().isBlank()
                || claim.getCategory() == null || claim.getStatement().isBlank()
                || claim.getDetail().isBlank()) {
            throw new IllegalArgumentException("claim projection is incomplete");
        }
        if (claim.getVerificationStatus() != com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("candidate claim must be verified");
        }
        if (!"APPROVED".equals(evidence.getPublicStatus())) {
            throw new IllegalArgumentException("candidate evidence must be approved");
        }
        if (!claim.getDirectEvidenceIds().contains(evidence.getEvidenceId())) {
            throw new IllegalArgumentException("claim and evidence link is incomplete");
        }
    }

    public String getSubjectId() { return subjectId; }
    public AnswerClaimProjection getClaim() { return claim; }
    public PublicEvidenceDescriptor getEvidence() { return evidence; }
    public String getRetrievalTarget() { return retrievalTarget; }
    public String getClaimId() { return claim.getId(); }
    public String getEvidenceCode() { return evidence.getEvidenceCode(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClaimEvidenceCandidate that)) return false;
        return subjectId.equals(that.subjectId) && claim.equals(that.claim)
                && evidence.equals(that.evidence) && retrievalTarget.equals(that.retrievalTarget);
    }

    @Override
    public int hashCode() { return Objects.hash(subjectId, claim, evidence, retrievalTarget); }

    @Override
    public String toString() {
        return "ClaimEvidenceCandidate{hasClaim=true, hasEvidence=true}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

