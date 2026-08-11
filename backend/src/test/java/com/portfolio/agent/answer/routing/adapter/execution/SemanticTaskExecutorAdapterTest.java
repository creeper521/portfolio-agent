package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.service.ConversationDraftValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SemanticTaskExecutorAdapterTest {

    @Test
    void portfolioExecutorMapsVerifiedClaimsAndEvidenceWithoutRerouting() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        PortfolioIntelligenceResult material = mock(PortfolioIntelligenceResult.class);
        PortfolioRetrievedPassage passage = mock(PortfolioRetrievedPassage.class);
        when(passage.getContent()).thenReturn("Verified public project detail");
        when(passage.getClaimId()).thenReturn("claim-01");
        when(passage.getEvidenceIds()).thenReturn(List.of("evidence-01"));
        when(material.getEvidence()).thenReturn(List.of(passage));
        when(material.getPortfolioRecommendation()).thenReturn(null);
        when(material.isDegraded()).thenReturn(false);
        when(intelligence.resolveTypedTask(any())).thenReturn(new PortfolioDecision(
                PortfolioDisposition.ANSWERED, material));
        PortfolioSemanticTaskExecutor executor = new PortfolioSemanticTaskExecutor(intelligence);

        TaskOutcome outcome = executor.execute(portfolioFact("task-01"), List.of());

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertEquals(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, outcome.getSourceDomain());
        assertEquals(List.of("claim-01"), outcome.getProvenance().orElseThrow().getClaimIds());
        assertEquals(List.of("evidence-01"), outcome.getProvenance().orElseThrow().getEvidenceIds());
        assertFalse(outcome.getResultPayload().isEmpty());
        verify(intelligence).resolveTypedTask(any());
        verify(intelligence, never()).tryResolve(any());
    }

    @Test
    void portfolioExecutorRejectsUnsupportedTaskTypeWithoutCallingCapability() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        PortfolioSemanticTaskExecutor executor = new PortfolioSemanticTaskExecutor(intelligence);

        TaskOutcome outcome = executor.execute(generalExplanation("task-01"), List.of());

        assertEquals(TaskOutcome.TaskResolution.NOT_SUPPORTED, outcome.getResolution());
        assertTrue(outcome.getResultPayload().isEmpty());
        verifyNoInteractions(intelligence);
    }

    @Test
    void validRefinementResolvesTypedRecommendationContextAndExecutes() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        PortfolioIntelligenceResult material = mock(PortfolioIntelligenceResult.class);
        PortfolioRetrievedPassage passage = mock(PortfolioRetrievedPassage.class);
        when(passage.getContent()).thenReturn("Verified refinement evidence");
        when(passage.getClaimId()).thenReturn("claim-refine");
        when(passage.getEvidenceIds()).thenReturn(List.of("evidence-refine"));
        when(material.getEvidence()).thenReturn(List.of(passage));
        when(material.isDegraded()).thenReturn(false);
        when(intelligence.resolveTypedTask(any())).thenReturn(new PortfolioDecision(
                PortfolioDisposition.ANSWERED, material));
        String batchId = "rec_" + "a".repeat(64);
        PortfolioRecommendationContext context = recommendationContext(batchId, "public-v1");
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                batchId,
                context,
                List.of(new PortfolioRecommendationItem(
                        "project-a", "Project A", "project-a", List.of("fit"),
                        List.of("evidence-refine"))),
                List.of(),
                List.of());
        when(material.getPortfolioRecommendation()).thenReturn(recommendation);
        PortfolioSemanticTaskExecutor executor = new PortfolioSemanticTaskExecutor(
                intelligence, ignored -> java.util.Optional.of(context), "public-v1");

        TaskOutcome outcome = executor.execute(refinement("task-04", batchId), List.of());

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        org.mockito.ArgumentCaptor<com.portfolio.agent.answer.intelligence.domain.PortfolioTask> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.portfolio.agent.answer.intelligence.domain.PortfolioTask.class);
        verify(intelligence).resolveTypedTask(captor.capture());
        assertEquals(com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode.REFINE_RECOMMENDATION,
                captor.getValue().getMode());
        assertEquals(context, captor.getValue().getRecommendationContext());
        assertEquals(Set.of("project-b"), captor.getValue().getRefinement().getExcludedPortfolioIds());
        assertTrue(outcome.getResultPayload().orElseThrow()
                instanceof TaskResultPayload.RecommendationResultPayload);
        TaskResultPayload.RecommendationResultPayload payload =
                (TaskResultPayload.RecommendationResultPayload) outcome.getResultPayload().orElseThrow();
        assertEquals("project-a", payload.getItems().get(0).getPortfolioId());
        assertEquals("project-a", payload.getItems().get(0).getRoute());
    }

    @Test
    void missingRefinementContextDoesNotCallPortfolioCapability() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        PortfolioSemanticTaskExecutor executor = new PortfolioSemanticTaskExecutor(
                intelligence, ignored -> java.util.Optional.empty(), "public-v1");

        TaskOutcome outcome = executor.execute(refinement("task-04", "rec_" + "a".repeat(64)), List.of());

        assertEquals(TaskOutcome.TaskResolution.NOT_SUPPORTED, outcome.getResolution());
        assertTrue(outcome.getReasonCodes().contains("PORTFOLIO_REFINEMENT_CONTEXT_MISSING"));
        verifyNoInteractions(intelligence);
    }

    @Test
    void staleRefinementContextDoesNotCallPortfolioCapability() {
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        String batchId = "rec_" + "a".repeat(64);
        PortfolioSemanticTaskExecutor executor = new PortfolioSemanticTaskExecutor(
                intelligence, ignored -> java.util.Optional.of(recommendationContext(batchId, "old-v1")),
                "public-v1");

        TaskOutcome outcome = executor.execute(refinement("task-04", batchId), List.of());

        assertEquals(TaskOutcome.TaskResolution.NOT_SUPPORTED, outcome.getResolution());
        assertTrue(outcome.getReasonCodes().contains("PORTFOLIO_REFINEMENT_CONTEXT_STALE"));
        verifyNoInteractions(intelligence);
    }

    @Test
    void generalUnavailableDoesNotBecomePortfolioFallback() {
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        ConversationDraftValidator validator = mock(ConversationDraftValidator.class);
        GeneralSemanticTaskExecutor executor = new GeneralSemanticTaskExecutor(
                new ConversationProviderAccess(false), modelPort, validator);

        TaskOutcome outcome = executor.execute(generalExplanation("task-01"), List.of());

        assertEquals(TaskOutcome.TaskResolution.CAPABILITY_UNAVAILABLE, outcome.getResolution());
        assertEquals(SemanticRoutingTypes.TaskSourceDomain.GENERAL, outcome.getSourceDomain());
        assertTrue(outcome.getResultPayload().isEmpty());
        verifyNoInteractions(modelPort, validator);
    }

    @Test
    void generalExecutorUsesGenerationAndDraftValidationWithoutToolPlanning() {
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        ConversationDraftValidator validator = mock(ConversationDraftValidator.class);
        ConversationDraft draft = new ConversationDraft(
                "General answer",
                AnswerResolution.ANSWERED,
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL,
                        "General explanation", List.of(), List.of())));
        when(modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.success(draft));
        when(validator.validate(any(), any(), any())).thenReturn(
                ConversationDraftValidationResult.valid(draft, draft.getBlocks()));
        GeneralSemanticTaskExecutor executor = new GeneralSemanticTaskExecutor(
                new ConversationProviderAccess(true), modelPort, validator);

        TaskOutcome outcome = executor.execute(generalExplanation("task-01"), List.of());

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertEquals(List.of("General explanation"), ((TaskResultPayload.SectionResultPayload)
                outcome.getResultPayload().orElseThrow()).getBlocks());
        verify(modelPort).generate(any(), any(), any(), any());
        verify(validator).validate(any(), any(), any());
        verify(modelPort, never()).classify(any(), any(), any());
        verify(modelPort, never()).planTools(any(), any(), any(), any(), any(), any());
    }

    @Test
    void synthesisReusesOnlySuccessfulUpstreamEvidence() {
        DeterministicSynthesisTaskExecutor executor = new DeterministicSynthesisTaskExecutor();
        TaskOutcome portfolio = TaskOutcome.answered(
                "task-01",
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("Portfolio evidence"), "Portfolio"),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        List.of("claim-01"), List.of("evidence-01")),
                false);
        TaskOutcome general = TaskOutcome.answered(
                "task-02",
                SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                new TaskResultPayload.SectionResultPayload(List.of("General explanation"), "General"),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                        List.of(), List.of()),
                false);

        TaskOutcome outcome = executor.execute(synthesis("task-03", List.of("task-01", "task-02")),
                List.of(portfolio, general));

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertEquals(TaskResultProvenance.DerivationType.SYNTHESIZED,
                outcome.getProvenance().orElseThrow().getDerivationType());
        assertEquals(Set.of(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        SemanticRoutingTypes.TaskSourceDomain.GENERAL),
                outcome.getProvenance().orElseThrow().getOriginDomains());
        assertEquals(List.of("evidence-01"), outcome.getProvenance().orElseThrow().getEvidenceIds());
    }

    @Test
    void synthesisBlocksWhenFewerThanTwoSuccessfulInputsAreAvailable() {
        DeterministicSynthesisTaskExecutor executor = new DeterministicSynthesisTaskExecutor();
        TaskOutcome portfolio = TaskOutcome.answered(
                "task-01",
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("Portfolio evidence"), "Portfolio"),
                TaskResultProvenance.direct(
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        List.of("claim-01"), List.of("evidence-01")),
                false);

        TaskOutcome outcome = executor.execute(synthesis("task-03", List.of("task-01", "task-02")),
                List.of(portfolio));

        assertEquals(TaskOutcome.TaskExecutionStatus.BLOCKED, outcome.getExecutionStatus());
        assertTrue(outcome.getResultPayload().isEmpty());
    }

    private static SemanticTask portfolioFact(String taskId) {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Explain project A",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of(subject));
    }

    private static SemanticTask generalExplanation(String taskId) {
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                "Explain CAP theorem",
                new SemanticTaskParameters.GeneralExplanation("CAP theorem", "STANDARD", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of());
    }

    private static SemanticTask refinement(String taskId, String batchId) {
        SubjectReference base = SubjectReference.result(batchId);
        SubjectReference removed = SubjectReference.project("project-b", "public-v1");
        SemanticTaskParameters.PortfolioRefinement parameters =
                new SemanticTaskParameters.PortfolioRefinement(base, Set.of(), Set.of(removed));
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Refine the recommendation",
                parameters,
                Set.of(SemanticRoutingTypes.RequestedOutput.RECOMMENDATION),
                TaskConfidence.highRule(),
                List.of(base, removed));
    }

    private static PortfolioRecommendationContext recommendationContext(
            String batchId, String contentVersion) {
        return new PortfolioRecommendationContext(
                batchId,
                contentVersion,
                "BACKEND_ENGINEERING",
                "INTERVIEWER",
                Set.of("JAVA"),
                3,
                List.of("project-a", "project-b"));
    }

    private static SemanticTask synthesis(String taskId, List<String> sourceTaskIds) {
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.SYNTHESIS,
                SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS,
                "Synthesize the results",
                new SemanticTaskParameters.Synthesis(sourceTaskIds, "Combine results", Set.of("IMPACT")),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of());
    }
}
