package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CandidateSubject;
import com.portfolio.agent.turn.capability.portfolio.retrieval.ClaimEvidenceCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PublicEvidenceDescriptor;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioCandidateSet;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidencePromotionValidatorTest {
    private final EvidencePromotionValidator validator = new EvidencePromotionValidator(
            Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void promotesOnceToTypedPublicUnitsWithoutAttemptMetadata() {
        ValidatedEvidenceBundle bundle = validator.promote(
                candidateSet(List.of(subject("project-a", "evidence-a", "claim-a"))), "public-v1");
        assertThat(bundle.getUnits()).singleElement().satisfies(unit -> {
            assertThat(unit.getClaim().getId()).isEqualTo("claim-a");
            assertThat(unit.getSourceReference().getReferenceKey()).isEqualTo("evidence-a");
            assertThat(unit.getSourceReference().getSubjectRoute()).isEqualTo("/projects/project-a");
        });
    }

    @Test
    void rejectsReleaseMismatchExpiryAndDuplicateClaimEvidenceAtomically() {
        PortfolioCandidateSet candidates = candidateSet(
                List.of(subject("project-a", "evidence-a", "claim-a")));
        assertThatThrownBy(() -> validator.promote(candidates, "public-v2"))
                .isInstanceOf(IllegalArgumentException.class);

        CandidateSubject one = subject("project-a", "evidence-a", "claim-a");
        CandidateSubject duplicate = new CandidateSubject(
                one.getSubjectId(), one.getSubjectRoute(), one.getTitle(), one.getContentVersion(),
                List.of(one.getCandidates().getFirst(), one.getCandidates().getFirst()));
        assertThatThrownBy(() -> validator.promote(
                candidateSet(List.of(duplicate)), "public-v1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validator.promote(
                candidateSet(List.of(subject(
                        "project-a", "expired", "claim-expired", LocalDate.of(2026, 1, 1)))),
                "public-v1")).isInstanceOf(IllegalArgumentException.class);
    }

    private PortfolioCandidateSet candidateSet(List<CandidateSubject> subjects) {
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.exact(subjects.stream()
                .map(subject -> new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, subject.getSubjectId(),
                        GoalSubjectReference.Basis.CONTINUATION, null)).toList(), "public-v1");
        return new PortfolioCandidateSet("public-v1", scope, subjects);
    }

    private CandidateSubject subject(String subjectId, String evidenceId, String claimId) {
        return subject(subjectId, evidenceId, claimId, LocalDate.of(2026, 12, 31));
    }

    private CandidateSubject subject(
            String subjectId, String evidenceId, String claimId, LocalDate validUntil) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId, AnswerClaimCategory.IMPLEMENTATION,
                "A verified statement", "A verified detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                evidenceId, "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/" + subjectId, "/evidence/" + evidenceId, validUntil);
        return new CandidateSubject(subjectId, "/projects/" + subjectId,
                "Project " + subjectId, "public-v1",
                List.of(new ClaimEvidenceCandidate(subjectId, claim, evidence, "IMPLEMENTATION")));
    }
}
