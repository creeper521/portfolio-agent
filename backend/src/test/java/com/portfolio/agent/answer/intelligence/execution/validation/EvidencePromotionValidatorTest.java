package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateCoverageReport;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.domain.ClaimEvidenceCandidate;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidencePromotionValidatorTest {

    @Test
    void promotesVerifiedCandidatesAndDropsRetrievalOnlyMetadata() {
        PortfolioRetrievalCandidateSet candidates = candidateSet(
                List.of(subject("project-a", "evidence-a")));

        ValidatedEvidenceBundle bundle = new EvidencePromotionValidator()
                .promote(candidates, "public-v1");

        assertEquals(1, bundle.getAcceptedUnitCount());
        assertEquals("claim-evidence-a", bundle.getUnits().get(0).getClaimId());
        assertEquals("evidence-a", bundle.getUnits().get(0).getSourceReference().getReferenceKey());
    }

    @Test
    void rejectsVersionMismatchAndSameClaimEvidenceDuplicateButAllowsEvidenceReuseAcrossClaims() {
        PortfolioRetrievalCandidateSet versioned = candidateSet(
                List.of(subject("project-a", "evidence-a")));
        assertThrows(IllegalArgumentException.class,
                () -> new EvidencePromotionValidator().promote(versioned, "public-v2"));

        PortfolioRetrievalCandidateSet sharedEvidence = candidateSet(List.of(
                subject("project-a", "evidence-a", "claim-a"),
                subject("project-b", "evidence-a", "claim-b")));
        assertEquals(2, new EvidencePromotionValidator()
                .promote(sharedEvidence, "public-v1").getAcceptedUnitCount());

        CandidateSubject duplicateSubject = subject("project-a", "evidence-a");
        CandidateSubject sameClaimEvidenceTwice = new CandidateSubject(
                duplicateSubject.getSubjectId(), duplicateSubject.getSubjectRoute(), duplicateSubject.getTitle(),
                duplicateSubject.getContentVersion(), List.of(
                        duplicateSubject.getCandidates().get(0), duplicateSubject.getCandidates().get(0)));
        PortfolioRetrievalCandidateSet duplicateCandidate = candidateSet(
                List.of(sameClaimEvidenceTwice));
        assertThrows(IllegalArgumentException.class,
                () -> new EvidencePromotionValidator().promote(duplicateCandidate, "public-v1"));
    }

    private static PortfolioRetrievalCandidateSet candidateSet(List<CandidateSubject> subjects) {
        List<SubjectReference> scopeSubjects = subjects.stream()
                .map(subject -> SubjectReference.project(subject.getSubjectId(), "public-v1"))
                .toList();
        return new PortfolioRetrievalCandidateSet(
                "PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(scopeSubjects, "public-v1"), subjects,
                new CandidateCoverageReport(Map.of("scope", CandidateCoverageReport.CoverageStatus.MATCHED)));
    }

    private static CandidateSubject subject(String subjectId, String evidenceCode) {
        return subject(subjectId, evidenceCode, "claim-" + evidenceCode);
    }

    private static CandidateSubject subject(
            String subjectId, String evidenceCode, String claimId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId, AnswerClaimCategory.IMPLEMENTATION,
                "A verified statement", "A verified detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceCode));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                evidenceCode, "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/" + subjectId, "/evidence/" + evidenceCode,
                LocalDate.of(2026, 12, 31));
        ClaimEvidenceCandidate candidate = new ClaimEvidenceCandidate(
                subjectId, claim, evidence, "IMPLEMENTATION");
        return new CandidateSubject(subjectId, "/projects/" + subjectId,
                "Project " + subjectId, "public-v1", List.of(candidate));
    }
}
