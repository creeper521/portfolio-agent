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
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
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
                candidateSet(List.of(subject("project-a", "evidence-a", "claim-a"))),
                "public-v1", java.util.Set.of(PortfolioSubjectKind.PROJECT));
        assertThat(bundle.getUnits()).singleElement().satisfies(unit -> {
            assertThat(unit.getSubjectKind()).isEqualTo(PortfolioSubjectKind.PROJECT);
            assertThat(unit.getClaim().getId()).isEqualTo("claim-a");
            assertThat(unit.getSourceReference().getReferenceKey()).isEqualTo("evidence-a");
            assertThat(unit.getSourceReference().getSubjectRoute()).isEqualTo("/projects/project-a");
        });
    }

    @Test
    void rejectsReleaseMismatchExpiryAndDuplicateClaimEvidenceAtomically() {
        PortfolioCandidateSet candidates = candidateSet(
                List.of(subject("project-a", "evidence-a", "claim-a")));
        assertThatThrownBy(() -> validator.promote(
                candidates, "public-v2", java.util.Set.of(PortfolioSubjectKind.PROJECT)))
                .isInstanceOf(IllegalArgumentException.class);

        CandidateSubject one = subject("project-a", "evidence-a", "claim-a");
        CandidateSubject duplicate = new CandidateSubject(
                one.getSubjectId(), one.getSubjectKind(), one.getSubjectRoute(),
                one.getTitle(), one.getContentVersion(),
                List.of(one.getCandidates().getFirst(), one.getCandidates().getFirst()));
        assertThatThrownBy(() -> validator.promote(
                candidateSet(List.of(duplicate)), "public-v1",
                java.util.Set.of(PortfolioSubjectKind.PROJECT)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validator.promote(
                candidateSet(List.of(subject(
                        "project-a", "expired", "claim-expired", LocalDate.of(2026, 1, 1)))),
                "public-v1", java.util.Set.of(PortfolioSubjectKind.PROJECT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disallowedKindRejectsTheWholeBatchWithTypedIntegrityFailure() {
        CandidateSubject project = subject("project-a", "evidence-a", "claim-a");
        CandidateSubject caseSubject = new CandidateSubject(
                "case-a", PortfolioSubjectKind.CASE, "/cases/case-a", "Case A", "public-v1",
                List.of(candidate("case-a", "case-evidence", "case-claim")));
        PortfolioCandidateSet candidates = new PortfolioCandidateSet(
                "public-v1", AuthorizedSubjectScope.allPublished("public-v1"),
                List.of(project, caseSubject));

        assertThatThrownBy(() -> validator.promote(
                candidates, "public-v1", java.util.Set.of(PortfolioSubjectKind.PROJECT)))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class)
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getFailure())
                        .isEqualTo(com.portfolio.agent.turn.capability.portfolio.retrieval
                                .RetrievalAttemptFailure.INTEGRITY_FAILURE))
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getIntegrityReason())
                        .contains(PortfolioEvidenceCapability.IntegrityReason
                                .SUBJECT_KIND_NOT_ALLOWED));
    }

    @Test
    void exactScopeMatchesTheTypedSubjectPairWithoutUsingTheRoute() {
        CandidateSubject wrongKind = new CandidateSubject(
                "project-a", PortfolioSubjectKind.CASE, "/projects/project-a",
                "Project A", "public-v1",
                List.of(candidate("project-a", "evidence-a", "claim-a")));
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.exact(List.of(
                new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, "project-a",
                        GoalSubjectReference.Basis.CONTINUATION, null)), "public-v1");

        assertThatThrownBy(() -> new PortfolioCandidateSet(
                "public-v1", scope, List.of(wrongKind)))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class)
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getIntegrityReason())
                        .contains(PortfolioEvidenceCapability.IntegrityReason
                                .EXACT_SCOPE_SUBJECT_KIND_MISMATCH));
    }

    @Test
    void candidateKindParticipatesInIdentityAndSafeDiagnostics() {
        CandidateSubject project = subject("project-a", "evidence-a", "claim-a");
        CandidateSubject sameContentAsCase = new CandidateSubject(
                project.getSubjectId(), PortfolioSubjectKind.CASE,
                project.getSubjectRoute(), project.getTitle(), project.getContentVersion(),
                project.getCandidates());

        assertThat(project).isNotEqualTo(sameContentAsCase);
        assertThat(project.toString())
                .contains("subjectKind=PROJECT", "candidateCount=1")
                .doesNotContain(project.getSubjectId(), project.getSubjectRoute(),
                        project.getTitle());
    }

    @Test
    void candidateRequiresAnExplicitSubjectKind() {
        CandidateSubject project = subject("project-a", "evidence-a", "claim-a");

        assertThatThrownBy(() -> new CandidateSubject(
                project.getSubjectId(), null, project.getSubjectRoute(), project.getTitle(),
                project.getContentVersion(), project.getCandidates()))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class)
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getIntegrityReason())
                        .contains(PortfolioEvidenceCapability.IntegrityReason
                                .SUBJECT_KIND_NOT_ALLOWED));
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
        return new CandidateSubject(subjectId, PortfolioSubjectKind.PROJECT,
                "/projects/" + subjectId, "Project " + subjectId, "public-v1",
                List.of(candidate(subjectId, evidenceId, claimId, validUntil)));
    }

    private ClaimEvidenceCandidate candidate(
            String subjectId, String evidenceId, String claimId) {
        return candidate(subjectId, evidenceId, claimId, LocalDate.of(2026, 12, 31));
    }

    private ClaimEvidenceCandidate candidate(
            String subjectId, String evidenceId, String claimId, LocalDate validUntil) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId, AnswerClaimCategory.IMPLEMENTATION,
                "A verified statement", "A verified detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                evidenceId, "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/" + subjectId, "/evidence/" + evidenceId, validUntil);
        return new ClaimEvidenceCandidate(subjectId, claim, evidence, "IMPLEMENTATION");
    }
}
