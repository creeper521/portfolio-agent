package com.portfolio.agent.answer.intelligence.execution.capability;

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
import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.ClaimEvidenceCandidate;
import com.portfolio.agent.answer.intelligence.execution.domain.EvidenceSelectionPolicy;
import com.portfolio.agent.answer.intelligence.execution.domain.FacetRetrievalProfile;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.answer.intelligence.retrieval.EffectiveRetrievalPlan;
import com.portfolio.agent.answer.intelligence.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.answer.intelligence.retrieval.RetrievalIntent;
import com.portfolio.agent.answer.intelligence.retrieval.SearchStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPortfolioEvidenceCapabilityTest {
    @Test
    void primarySuccessDoesNotInvokeFallback() {
        CountingPort primary = new CountingPort(CapabilityExecutionResult.success(candidateSet(),
                new com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator()
                        .promote(candidateSet(), "public-v1")));
        CountingPort fallback = new CountingPort(CapabilityExecutionResult.unavailable(
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE));

        CapabilityExecutionResult result = new DefaultPortfolioEvidenceCapability(primary, fallback)
                .execute(invocation(), constraints());

        assertEquals(CapabilityExecutionResult.Status.SUCCESS, result.getStatus());
        assertEquals(1, primary.calls);
        assertEquals(0, fallback.calls);
    }

    @Test
    void unavailablePrimaryUsesOneAtomicFallbackAndMarksDegraded() {
        CountingPort primary = new CountingPort(CapabilityExecutionResult.unavailable(
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE));
        CountingPort fallback = new CountingPort(CapabilityExecutionResult.empty(
                emptyCandidateSet(), new com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator()
                        .promote(emptyCandidateSet(), "public-v1")));

        CapabilityExecutionResult result = new DefaultPortfolioEvidenceCapability(primary, fallback)
                .execute(invocation(), constraints());

        assertEquals(CapabilityExecutionResult.Status.EMPTY, result.getStatus());
        assertTrue(result.isDegraded());
        assertEquals(1, primary.calls);
        assertEquals(1, fallback.calls);
    }

    @Test
    void integrityFailureDoesNotInvokeFallback() {
        CountingPort primary = new CountingPort(CapabilityExecutionResult.integrityFailed());
        CountingPort fallback = new CountingPort(CapabilityExecutionResult.unavailable(
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE));

        CapabilityExecutionResult result = new DefaultPortfolioEvidenceCapability(primary, fallback)
                .execute(invocation(), constraints());

        assertEquals(CapabilityExecutionResult.Status.INTEGRITY_FAILED, result.getStatus());
        assertEquals(0, fallback.calls);
    }

    @Test
    void vectorFailureExecutesKeywordPlanOnTheSameBackend() {
        CountingPort primary = new CountingPort(CapabilityExecutionResult.vectorUnavailable(),
                CapabilityExecutionResult.empty(emptyCandidateSet(),
                        new com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator()
                                .promote(emptyCandidateSet(), "public-v1")));
        CountingPort fallback = new CountingPort(CapabilityExecutionResult.unavailable(
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE));
        EffectiveRetrievalPlan plan = new EffectiveRetrievalPlan(
                RetrievalIntent.EXACT_SUBJECT, CorpusBackend.POSTGRESQL,
                SearchStrategy.HYBRID, null, null, "public-v1");
        PortfolioEvidenceInvocation invocation = invocation().withRetrievalPlan(plan);

        CapabilityExecutionResult result = new DefaultPortfolioEvidenceCapability(
                primary, fallback,
                new com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator(),
                new RetrievalFallbackPolicy()).execute(invocation, constraints());

        assertEquals(CapabilityExecutionResult.Status.EMPTY, result.getStatus());
        assertTrue(result.isDegraded());
        assertEquals(2, primary.calls);
        assertEquals(0, fallback.calls);
        assertEquals(SearchStrategy.KEYWORD, primary.lastInvocation.getRetrievalPlan().getPrimaryStrategy());
    }

    private static CapabilityExecutionConstraints constraints() {
        return new CapabilityExecutionConstraints(TaskExecutionAllowance.portfolio(
                Instant.now().plusSeconds(10)));
    }

    private static PortfolioEvidenceInvocation invocation() {
        return new PortfolioEvidenceInvocation(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("project-a", "public-v1")), "public-v1"),
                List.of(FacetRetrievalProfile.forFacet(
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet.IMPLEMENTATION)),
                List.of(), EvidenceSelectionPolicy.defaults(), "public-v1");
    }

    private static PortfolioRetrievalCandidateSet candidateSet() {
        ClaimEvidenceCandidate candidate = candidate();
        CandidateSubject subject = new CandidateSubject(
                "project-a", "/projects/project-a", "Project A", "public-v1", List.of(candidate));
        return new PortfolioRetrievalCandidateSet("PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("project-a", "public-v1")), "public-v1"),
                List.of(subject), new CandidateCoverageReport(Map.of(
                        "project-a/IMPLEMENTATION", CandidateCoverageReport.CoverageStatus.MATCHED)));
    }

    private static PortfolioRetrievalCandidateSet emptyCandidateSet() {
        return new PortfolioRetrievalCandidateSet("PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, "public-v1",
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("project-a", "public-v1")), "public-v1"),
                List.of(), new CandidateCoverageReport(Map.of(
                        "project-a/IMPLEMENTATION", CandidateCoverageReport.CoverageStatus.EVALUATED_NO_QUALIFYING_MATCH)));
    }

    private static ClaimEvidenceCandidate candidate() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-a", AnswerClaimCategory.IMPLEMENTATION, "Statement", "Detail",
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY, List.of("evidence-a"));
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                "evidence-a", "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/project-a", "/evidence/evidence-a", LocalDate.of(2026, 12, 31));
        return new ClaimEvidenceCandidate("project-a", claim, evidence, "IMPLEMENTATION");
    }

    private static final class CountingPort implements PortfolioCandidateRetrievalPort {
        private final CapabilityExecutionResult result;
        private final CapabilityExecutionResult secondResult;
        private int calls;
        private PortfolioEvidenceInvocation lastInvocation;

        private CountingPort(CapabilityExecutionResult result) { this(result, result); }
        private CountingPort(CapabilityExecutionResult result, CapabilityExecutionResult secondResult) {
            this.result = result;
            this.secondResult = secondResult;
        }

        @Override
        public CapabilityExecutionResult retrieve(
                PortfolioEvidenceInvocation invocation,
                CapabilityExecutionConstraints constraints) {
            calls++;
            lastInvocation = invocation;
            return calls == 1 ? result : secondResult;
        }
    }
}
