package com.portfolio.agent.answer.intelligence.execution.support;

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
import com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceSupportAssessorTest {
    @Test
    void factAssessesEachFacetAndReportsMissingLabels() {
        ValidatedEvidenceBundle bundle = bundle(List.of(
                candidate("project-a", "implementation", AnswerClaimCategory.IMPLEMENTATION)));
        SemanticTaskParameters.PortfolioFact fact = new SemanticTaskParameters.PortfolioFact(
                SubjectReference.project("project-a", "public-v1"),
                Set.of("IMPLEMENTATION", "OUTCOME"), "GUEST");

        EvidenceSupportAssessment assessment = new EvidenceSupportAssessor().assessFact(fact, bundle);

        assertEquals(EvidenceSupportAssessment.SupportStatus.PARTIAL, assessment.getStatus());
        assertEquals(List.of("OUTCOME"), assessment.getOmittedLabels());
        assertEquals(1, assessment.getUnitsByCriterion().get("project-a/IMPLEMENTATION").size());
    }

    @Test
    void compareRequiresEverySubjectForTheSameDimension() {
        ValidatedEvidenceBundle bundle = bundle(List.of(
                candidate("project-a", "implementation-a", AnswerClaimCategory.IMPLEMENTATION),
                candidate("project-b", "implementation-b", AnswerClaimCategory.IMPLEMENTATION)));
        SemanticTaskParameters.PortfolioCompare compare = new SemanticTaskParameters.PortfolioCompare(
                List.of(SubjectReference.project("project-a", "public-v1"),
                        SubjectReference.project("project-b", "public-v1")),
                Set.of("IMPLEMENTATION", "IMPACT"), "GUEST");

        EvidenceSupportAssessment assessment = new EvidenceSupportAssessor().assessCompare(compare, bundle);

        assertEquals(EvidenceSupportAssessment.SupportStatus.PARTIAL, assessment.getStatus());
        assertEquals(List.of("IMPACT"), assessment.getOmittedLabels());
    }

    @Test
    void recommendationSelectsEvidenceFromTheRequestedNumberOfDistinctSubjects() {
        ValidatedEvidenceBundle bundle = bundle(List.of(
                candidate("project-a", "responsibility-a", AnswerClaimCategory.RESPONSIBILITY),
                candidate("project-a", "verification-a", AnswerClaimCategory.VERIFICATION),
                candidate("project-b", "responsibility-b", AnswerClaimCategory.RESPONSIBILITY)));
        SemanticTaskParameters.PortfolioRecommend recommendation =
                new SemanticTaskParameters.PortfolioRecommend(
                        List.of(), "BACKEND_ENGINEERING", Set.of(), "岗位匹配", 2, "GUEST");

        EvidenceSupportAssessment assessment =
                new EvidenceSupportAssessor().assessRecommendation(recommendation, bundle);

        assertEquals(List.of("project-a", "project-b"), assessment.getUnitsByCriterion()
                .get(RecommendationProfiles.PUBLIC_DELIVERY_EVIDENCE).stream()
                .map(unit -> unit.getSubjectId())
                .toList());
    }

    private static ValidatedEvidenceBundle bundle(List<ClaimEvidenceCandidate> candidates) {
        Map<String, List<ClaimEvidenceCandidate>> candidatesBySubject = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesBySubject
                .computeIfAbsent(candidate.getSubjectId(), ignored -> new ArrayList<>())
                .add(candidate));
        List<CandidateSubject> subjects = candidatesBySubject.entrySet().stream()
                .map(entry -> new CandidateSubject(entry.getKey(),
                        "/projects/" + entry.getKey(), entry.getKey(), "public-v1", entry.getValue()))
                .toList();
        List<SubjectReference> references = subjects.stream()
                .map(subject -> SubjectReference.project(subject.getSubjectId(), "public-v1"))
                .toList();
        PortfolioRetrievalCandidateSet candidateSet = new PortfolioRetrievalCandidateSet(
                "PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(references, "public-v1"), subjects,
                new CandidateCoverageReport(Map.of("scope", CandidateCoverageReport.CoverageStatus.MATCHED)));
        return new EvidencePromotionValidator().promote(candidateSet, "public-v1");
    }

    private static ClaimEvidenceCandidate candidate(
            String subjectId, String evidenceCode, AnswerClaimCategory category) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-" + evidenceCode, category, "Statement", "Detail",
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY, List.of(evidenceCode));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                evidenceCode, "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/" + subjectId, "/evidence/" + evidenceCode,
                LocalDate.of(2026, 12, 31));
        return new ClaimEvidenceCandidate(subjectId, claim, evidence, category.name());
    }
}
