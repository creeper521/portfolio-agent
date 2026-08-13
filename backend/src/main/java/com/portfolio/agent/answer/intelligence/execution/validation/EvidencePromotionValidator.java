package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.ClaimEvidenceCandidate;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** All-or-nothing promotion of a retrieval attempt into public evidence. */
public final class EvidencePromotionValidator {
    private final PublicReferenceValidator publicReferenceValidator;

    public EvidencePromotionValidator() { this(new PublicReferenceValidator()); }

    public EvidencePromotionValidator(PublicReferenceValidator publicReferenceValidator) {
        this.publicReferenceValidator = Objects.requireNonNull(publicReferenceValidator, "publicReferenceValidator");
    }

    public ValidatedEvidenceBundle promote(
            PortfolioRetrievalCandidateSet candidateSet, String expectedContentVersion) {
        Objects.requireNonNull(candidateSet, "candidateSet");
        if (!candidateSet.getReturnedContentVersion().equals(expectedContentVersion)) {
            throw new IllegalArgumentException("EVIDENCE_INTEGRITY_FAILURE");
        }
        List<ValidatedEvidenceUnit> units = new ArrayList<>();
        LinkedHashSet<String> claimEvidencePairs = new LinkedHashSet<>();
        for (com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject subject
                : candidateSet.getCandidateSubjects()) {
            for (ClaimEvidenceCandidate candidate : subject.getCandidates()) {
                if (!candidateSet.getReturnedContentVersion().equals(
                        candidate.getEvidence().getContentVersion())) {
                    throw new IllegalArgumentException("EVIDENCE_INTEGRITY_FAILURE");
                }
                if (candidate.getEvidence().getValidUntil() != null
                        && candidate.getEvidence().getValidUntil().isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("EVIDENCE_INTEGRITY_FAILURE");
                }
                PublicSourceReference reference = publicReferenceValidator.validate(candidate.getEvidence());
                String claimEvidencePair = candidate.getClaim().getId()
                        + "\u0000" + reference.getReferenceKey();
                if (!claimEvidencePairs.add(claimEvidencePair)) {
                    throw new IllegalArgumentException("EVIDENCE_INTEGRITY_FAILURE");
                }
                units.add(new ValidatedEvidenceUnit(subject.getSubjectId(), candidate.getClaim(), reference));
            }
        }
        return new ValidatedEvidenceBundle(
                candidateSet.getExecutedScope(), expectedContentVersion, units);
    }
}
