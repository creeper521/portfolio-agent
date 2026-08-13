package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatedEvidenceBundleTest {
    @Test
    void bundleDoesNotAcceptUnitsOutsideTheAuthorizedScope() {
        PublicSourceReference source = new PublicSourceReference(
                "evidence-a", com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/project-a", "/evidence/evidence-a");
        com.portfolio.agent.answer.domain.AnswerClaimProjection claim = new com.portfolio.agent.answer.domain.AnswerClaimProjection(
                "claim-a", com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION,
                "Statement", "Detail", com.portfolio.agent.answer.domain.AnswerAchievementStatus.IMPLEMENTED_TESTED,
                com.portfolio.agent.answer.domain.AnswerContributionType.PRIMARY,
                com.portfolio.agent.answer.domain.AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED,
                com.portfolio.agent.answer.domain.AnswerMateriality.KEY, List.of("evidence-a"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("project-b", claim, source);

        assertThrows(IllegalArgumentException.class, () -> new ValidatedEvidenceBundle(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("project-a", "public-v1")), "public-v1"),
                "public-v1", List.of(unit)));
    }

    @Test
    void bundleAcceptsCaseUnitsWithinTheAuthorizedCaseScope() {
        PublicSourceReference source = new PublicSourceReference(
                "evidence-case-a",
                com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor.SourceType.CODE,
                "/cases/case-a", "/evidence/evidence-case-a");
        com.portfolio.agent.answer.domain.AnswerClaimProjection claim =
                new com.portfolio.agent.answer.domain.AnswerClaimProjection(
                        "claim-case-a", com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION,
                        "Statement", "Detail",
                        com.portfolio.agent.answer.domain.AnswerAchievementStatus.IMPLEMENTED_TESTED,
                        com.portfolio.agent.answer.domain.AnswerContributionType.PRIMARY,
                        com.portfolio.agent.answer.domain.AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                        com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED,
                        com.portfolio.agent.answer.domain.AnswerMateriality.KEY,
                        List.of("evidence-case-a"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("case-a", claim, source);

        assertDoesNotThrow(() -> new ValidatedEvidenceBundle(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.caseReference("case-a", "public-v1")), "public-v1"),
                "public-v1", List.of(unit)));
    }
}
