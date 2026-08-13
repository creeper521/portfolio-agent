package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.PublicSourceReference;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import com.portfolio.agent.answer.routing.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactResultPolicyTest {
    @Test
    void unsupportedMaterialCarriesOmittedTopicsWithoutInventedStatements() {
        EvidenceSupportAssessment assessment = new EvidenceSupportAssessment(
                EvidenceSupportAssessment.SupportStatus.INSUFFICIENT, Map.of(), List.of("OUTCOME"));
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create("task-a",
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "private goal label",
                new SemanticTaskParameters.PortfolioFact(subject, java.util.Set.of(), "GUEST"),
                java.util.Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
        CandidateSubject publicSubject = new CandidateSubject("project-a", "/projects/a",
                "Project A", "public-v1", List.of());
        PortfolioAnswerMaterial material = new FactResultPolicy().material(
                task, null, assessment, List.of(publicSubject));
        assertEquals(List.of(), material.getStatements());
        assertEquals(List.of("OUTCOME"), material.getOmittedTopicLabels());
        assertEquals("Project A", material.getPublicTitle());
    }

    @Test
    void rejectsEvidenceWhosePublicRouteBelongsToAnotherSubject() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create("task-a",
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "private goal label",
                new SemanticTaskParameters.PortfolioFact(subject, java.util.Set.of(), "GUEST"),
                java.util.Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-a", AnswerClaimCategory.IMPLEMENTATION, "公开实现", "公开细节",
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("evidence-a"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("project-a", claim,
                new PublicSourceReference("evidence-a", PublicEvidenceDescriptor.SourceType.CODE,
                        "/projects/project-b", "/evidence/evidence-a"));
        EvidenceSupportAssessment assessment = new EvidenceSupportAssessment(
                EvidenceSupportAssessment.SupportStatus.SUFFICIENT,
                Map.of("IMPLEMENTATION", List.of(unit)), List.of());
        CandidateSubject publicSubject = new CandidateSubject("project-a", "/projects/project-a",
                "Project A", "public-v1", List.of());

        assertThrows(IllegalArgumentException.class, () -> new FactResultPolicy().material(
                task, null, assessment, List.of(publicSubject)));
    }
}
