package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.intelligence.execution.capability.CapabilityExecutionResult;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioEvidenceCapability;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateCoverageReport;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.domain.ClaimEvidenceCandidate;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioCapabilityCatalog;
import com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.service.DeterministicPortfolioAnswerComposer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P3PortfolioSemanticTaskExecutorTaskTypesTest {
    private static final String VERSION = "public-v1";

    @Test
    void comparesTwoPublishedSubjectsThroughTheSameBoundedCapability() {
        SubjectReference first = SubjectReference.project("project-a", VERSION);
        SubjectReference second = SubjectReference.project("project-b", VERSION);
        SemanticTask task = SemanticTask.create(
                "compare-task", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "compare implementation",
                new SemanticTaskParameters.PortfolioCompare(
                        List.of(first, second), Set.of("IMPLEMENTATION"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.COMPARISON), TaskConfidence.highRule(),
                List.of(first, second));

        TaskOutcome outcome = execute(task, candidateResult(
                List.of(first, second), AnswerClaimCategory.IMPLEMENTATION, "IMPLEMENTATION"));

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertTrue(outcome.getContribution().isPresent());
        assertEquals(2, outcome.getContribution().orElseThrow().getSupportedStatements().size());
    }

    @Test
    void recommendsFromPublishedEvidenceThroughTheSameBoundedCapability() {
        SubjectReference first = SubjectReference.project("project-a", VERSION);
        SubjectReference second = SubjectReference.project("project-b", VERSION);
        SemanticTask task = SemanticTask.create(
                "recommend-task", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "recommend backend projects",
                new SemanticTaskParameters.PortfolioRecommend(
                        List.of(first, second), "BACKEND_ENGINEERING", Set.of(),
                        "backend delivery", 2, "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(),
                List.of(first, second));

        TaskOutcome outcome = execute(task, candidateResult(
                List.of(first, second), AnswerClaimCategory.OUTCOME, "OUTCOME"));

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertTrue(outcome.getResultPayload().isPresent());
        TaskResultPayload.RecommendationResultPayload payload =
                (TaskResultPayload.RecommendationResultPayload)
                        outcome.getResultPayload().orElseThrow();
        assertEquals(2, payload.getProjection().getItems().size());
        assertEquals(List.of("project-a", "project-b"),
                payload.getProjection().getSelectedPortfolioIds());
        assertTrue(payload.getProjection().getItems().stream()
                .allMatch(item -> !item.getSourceReferences().isEmpty()));
    }

    private static TaskOutcome execute(SemanticTask task, CapabilityExecutionResult result) {
        PortfolioEvidenceCapability capability = mock(PortfolioEvidenceCapability.class);
        when(capability.execute(any(), any())).thenReturn(result);
        return new P3PortfolioSemanticTaskExecutor(
                new PortfolioCapabilityCatalog(), capability, new DeterministicPortfolioAnswerComposer())
                .execute(new SemanticTaskExecutionContext(
                        task, List.of(), List.of(), VERSION,
                        TaskExecutionAllowance.portfolio(Instant.now().plusSeconds(30)), List.of()));
    }

    private static CapabilityExecutionResult candidateResult(
            List<SubjectReference> references, AnswerClaimCategory category, String target) {
        List<CandidateSubject> subjects = new ArrayList<>();
        Map<String, CandidateCoverageReport.CoverageStatus> coverage = new LinkedHashMap<>();
        for (SubjectReference reference : references) {
            String subjectId = reference.getSubjectId();
            ClaimEvidenceCandidate candidate = new ClaimEvidenceCandidate(
                    subjectId,
                    new AnswerClaimProjection(
                            "claim-" + subjectId, category, "Statement " + subjectId,
                            "Detail " + subjectId, AnswerAchievementStatus.IMPLEMENTED_TESTED,
                            AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                            AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                            List.of("shared-evidence")),
                    new PublicEvidenceDescriptor(
                            "shared-evidence", VERSION, "APPROVED", PublicEvidenceDescriptor.SourceType.DOCUMENT,
                            "/projects/" + subjectId, "/evidence/shared-evidence", LocalDate.of(9999, 12, 31)),
                    target);
            subjects.add(new CandidateSubject(
                    subjectId, "/projects/" + subjectId, "Project " + subjectId, VERSION,
                    List.of(candidate)));
            coverage.put(subjectId + "/" + target, CandidateCoverageReport.CoverageStatus.MATCHED);
        }
        PortfolioRetrievalCandidateSet candidateSet = new PortfolioRetrievalCandidateSet(
                PortfolioCapabilityCatalog.CAPABILITY_ID, 1, VERSION,
                AuthorizedSubjectScope.exactSubjects(references, VERSION), subjects,
                new CandidateCoverageReport(coverage));
        return CapabilityExecutionResult.success(candidateSet,
                new EvidencePromotionValidator().promote(candidateSet, VERSION));
    }
}
