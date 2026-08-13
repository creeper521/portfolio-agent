package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioRetrievalCandidateSetTest {

    @Test
    void candidateSetBindsCapabilityAttemptVersionScopeAndCoverage() {
        ClaimEvidenceCandidate candidate = candidate("project-a", "evidence-a");
        CandidateSubject subject = new CandidateSubject(
                "project-a", "/projects/project-a", "Project A", "public-v1",
                List.of(candidate));
        PortfolioRetrievalCandidateSet set = new PortfolioRetrievalCandidateSet(
                "PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(
                        List.of(com.portfolio.agent.answer.routing.domain.SubjectReference.project(
                                "project-a", "public-v1")), "public-v1"),
                List.of(subject), new CandidateCoverageReport(Map.of("project-a/OVERVIEW",
                        CandidateCoverageReport.CoverageStatus.MATCHED)));

        assertEquals(1, set.getEvidenceUnitCount());
        assertEquals(CandidateCoverageReport.CoverageStatus.MATCHED,
                set.getCoverageReport().getStatus("project-a/OVERVIEW"));
    }

    @Test
    void candidateSetRejectsCrossSubjectAndBudgetOverflow() {
        ClaimEvidenceCandidate candidate = candidate("project-a", "evidence-a");
        CandidateSubject subject = new CandidateSubject(
                "project-a", "/projects/project-a", "Project A", "public-v1", List.of(candidate));

        assertThrows(IllegalArgumentException.class, () -> new PortfolioRetrievalCandidateSet(
                "PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(
                        List.of(com.portfolio.agent.answer.routing.domain.SubjectReference.project(
                                "project-b", "public-v1")), "public-v1"), List.of(subject),
                CandidateCoverageReport.of(List.of("project-a/OVERVIEW"),
                        CandidateCoverageReport.CoverageStatus.MATCHED)));
    }

    private static ClaimEvidenceCandidate candidate(String subjectId, String evidenceCode) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-a", AnswerClaimCategory.IMPLEMENTATION, "A statement", "A detail",
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY, List.of(evidenceCode));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                evidenceCode, "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/" + subjectId, "/evidence/" + evidenceCode, LocalDate.of(2026, 12, 31));
        return new ClaimEvidenceCandidate(subjectId, claim, evidence, "IMPLEMENTATION");
    }
}
