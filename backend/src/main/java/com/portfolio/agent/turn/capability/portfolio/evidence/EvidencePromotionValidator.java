package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.retrieval.ClaimEvidenceCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PublicEvidenceDescriptor;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioCandidateSet;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** The sole all-or-nothing raw-candidate promotion boundary. */
public final class EvidencePromotionValidator {
    private final Clock clock;
    public EvidencePromotionValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ValidatedEvidenceBundle promote(
            PortfolioCandidateSet candidateSet, String expectedContentReleaseId) {
        Objects.requireNonNull(candidateSet, "candidateSet");
        if (!expectedContentReleaseId.equals(candidateSet.getContentReleaseId())) {
            throw new IllegalArgumentException("CONTENT_RELEASE_MISMATCH");
        }
        List<ValidatedEvidenceUnit> units = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        candidateSet.getSubjects().forEach(subject -> subject.getCandidates().forEach(candidate -> {
            validate(candidate, expectedContentReleaseId);
            PublicEvidenceDescriptor evidence = candidate.getEvidence();
            String identity = candidate.getClaimId() + "\u0000" + evidence.getEvidenceCode();
            if (!identities.add(identity)) throw new IllegalArgumentException("INTEGRITY_FAILURE");
            units.add(new ValidatedEvidenceUnit(subject.getSubjectId(), candidate.getClaim(),
                    new PublicSourceReference(
                            evidence.getEvidenceCode(), evidence.getLabel(),
                            evidence.getContentVersion(), evidence.getSourceType().name(),
                            evidence.getSubjectRoute(), evidence.getEvidenceRoute())));
        }));
        return new ValidatedEvidenceBundle(
                candidateSet.getExecutedScope(), expectedContentReleaseId, units);
    }

    private void validate(ClaimEvidenceCandidate candidate, String releaseId) {
        PublicEvidenceDescriptor evidence = candidate.getEvidence();
        if (!releaseId.equals(evidence.getContentVersion())
                || !"APPROVED".equals(evidence.getPublicStatus())
                || evidence.getValidUntil() != null
                && evidence.getValidUntil().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("INTEGRITY_FAILURE");
        }
    }
}
