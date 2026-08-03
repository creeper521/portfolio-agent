package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.PortfolioReferenceContextRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationalAgentRuntimeTest {

    @Test
    void globalSafetyBoundaryPreemptsPortfolioIntelligence() {
        Fixture fixture = fixture(true);
        when(fixture.router.routeBoundary(anyString())).thenReturn(new ConversationRoute(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                ConversationAnswerScope.GLOBAL,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false));

        ConversationAnswerResult result = fixture.runtime.answer(request("show private token"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.REJECTED);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.GLOBAL);
        verifyNoInteractions(fixture.intelligence, fixture.modelPort);
    }

    @Test
    void delegatesExactlyOnceBeforeGeneralConversation() {
        Fixture fixture = fixture(true);
        when(fixture.intelligence.tryResolve(any())).thenReturn(
                new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null));
        ConversationDraft draft = generalDraft();
        when(fixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.success(draft));
        when(fixture.validator.validate(any(), any(), any())).thenReturn(
                ConversationDraftValidationResult.valid(draft, draft.getBlocks()));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("What is dependency injection?"));

        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.GENERAL);
        assertThat(result.getConstructionMode()).isEqualTo(AnswerConstructionMode.GENERAL_MODEL);
        assertThat(result.getIntentSource()).isEqualTo(AnswerIntentSource.GLOBAL);
        assertThat(result.getEvidenceState()).isEqualTo(AnswerEvidenceState.NOT_REQUIRED);
        verify(fixture.intelligence).tryResolve(any());
        verify(fixture.modelPort).generate(any(), any(), any(), any());
    }

    @Test
    void handledPortfolioDecisionDoesNotReturnToGeneralRouting() {
        Fixture fixture = fixture(true);
        when(fixture.intelligence.tryResolve(any())).thenReturn(answeredDecision());

        ConversationAnswerResult result = fixture.runtime.answer(
                request("How was the project verified?"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.PORTFOLIO);
        assertThat(result.getConstructionMode())
                .isEqualTo(AnswerConstructionMode.EVIDENCE_COMPOSITION);
        assertThat(result.getIntentSource()).isEqualTo(AnswerIntentSource.RULE);
        assertThat(result.getEvidenceState()).isEqualTo(AnswerEvidenceState.VERIFIED);
        assertThat(result.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getClaimIds()).containsExactly("claim-1");
            assertThat(block.getEvidenceIds()).containsExactly("evidence-1");
        });
        verifyNoInteractions(fixture.modelPort);
        verify(fixture.router, never()).route(any(), any(), any());
    }

    @Test
    void explicitReferenceIsPassedOnlyWhenTheRequestCarriesIt() {
        Fixture fixture = fixture(true);
        when(fixture.intelligence.tryResolve(any())).thenReturn(answeredDecision());
        PortfolioReferenceContextRequest reference = new PortfolioReferenceContextRequest(
                "public-1",
                List.of("project-one"),
                List.of(),
                null,
                List.of("claim-1"),
                AnswerSectionType.VERIFICATION,
                PortfolioFollowUpAction.SHOW_EVIDENCE);
        ConversationAnswerRequest request = request(
                "Show evidence",
                new ConversationAnswerContextRequest(
                        "project-one",
                        null,
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.PROJECT,
                        List.of(),
                        null,
                        reference));

        fixture.runtime.answer(request);

        ArgumentCaptor<PortfolioTurn> captor = ArgumentCaptor.forClass(PortfolioTurn.class);
        verify(fixture.intelligence).tryResolve(captor.capture());
        assertThat(captor.getValue().getReferenceContext()).isNotNull();
        assertThat(captor.getValue().getReferenceContext().getFollowUpAction())
                .isEqualTo(PortfolioFollowUpAction.SHOW_EVIDENCE);
        assertThat(captor.getValue().getReferenceContext().getReferencedClaimIds())
                .containsExactly("claim-1");
    }

    @Test
    void providerDisabledGeneralQuestionReturnsCapabilityUnavailable() {
        Fixture fixture = fixture(false);
        when(fixture.intelligence.tryResolve(any())).thenReturn(
                new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Explain dependency injection"));

        assertThat(result.getResolution())
                .isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.GENERAL);
        assertThat(result.getConstructionMode()).isEqualTo(AnswerConstructionMode.TEMPLATE);
        assertThat(result.getNoticeCode()).isEqualTo("GENERAL_MODEL_UNAVAILABLE");
        verifyNoInteractions(fixture.modelPort);
    }

    @Test
    void providerFailureDoesNotLetGeneralModelInventPortfolioFacts() {
        Fixture fixture = fixture(true);
        when(fixture.intelligence.tryResolve(any())).thenReturn(
                new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null));
        when(fixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(ConversationModelFailureCode.TIMEOUT));

        ConversationAnswerResult result = fixture.runtime.answer(request("Explain CAP theorem"));

        assertThat(result.getResolution())
                .isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(result.getBlocks()).allSatisfy(block -> {
            assertThat(block.getSourceScope()).isEqualTo(ConversationSourceScope.GENERAL);
            assertThat(block.getClaimIds()).isEmpty();
            assertThat(block.getEvidenceIds()).isEmpty();
        });
    }

    private Fixture fixture(boolean providerAllowed) {
        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        when(knowledgeGateway.getContent()).thenReturn(
                new RuntimeAnswerContent("public-1", "hash", List.of()));
        ConversationWindowManager windowManager = mock(ConversationWindowManager.class);
        when(windowManager.prepare(any(), any())).thenReturn(
                new ConversationWindow(null, List.of(), 0));
        ConversationIntentRouter router = mock(ConversationIntentRouter.class);
        PortfolioGroundingAssembler groundingAssembler = mock(PortfolioGroundingAssembler.class);
        when(groundingAssembler.assemble(any(), any(), any())).thenReturn(
                PortfolioGroundingContext.empty());
        ConversationToolService toolService = mock(ConversationToolService.class);
        when(toolService.enrich(any(), any(), any(), any(), any())).thenReturn(
                PortfolioGroundingContext.empty());
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        ConversationDraftValidator validator = mock(ConversationDraftValidator.class);
        DynamicQuestionService questionService = mock(DynamicQuestionService.class);
        when(questionService.generate(any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(List.of());
        PortfolioIntelligence intelligence = mock(PortfolioIntelligence.class);
        ConversationDecisionPublisher decisions = mock(ConversationDecisionPublisher.class);
        DiagnosticEventPublisher diagnostics = event -> { };
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                knowledgeGateway,
                windowManager,
                router,
                groundingAssembler,
                toolService,
                modelPort,
                validator,
                questionService,
                new DeterministicConversationFallback(),
                new ConversationProviderAccess(providerAllowed),
                intelligence,
                new PortfolioIntelligenceAnswerAssembler(),
                new ConversationProgressClassifier(),
                decisions,
                diagnostics);
        return new Fixture(runtime, router, modelPort, validator, intelligence);
    }

    private PortfolioDecision answeredDecision() {
        PortfolioRetrievedSubject subject = new PortfolioRetrievedSubject(
                "project-1",
                "PROJECT",
                "Project one",
                "Public summary",
                "/projects/project-one",
                "BACKEND",
                Set.of("JAVA"));
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "passage-1",
                "project-1",
                "claim-1",
                "Verified public material",
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-1", "Evidence one", "APPROVED")));
        PortfolioIntelligenceResult material = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject),
                List.of(passage),
                null,
                null,
                "public-1",
                false,
                null,
                AnswerIntentSource.RULE,
                false);
        return new PortfolioDecision(PortfolioDisposition.ANSWERED, material);
    }

    private ConversationDraft generalDraft() {
        return new ConversationDraft(
                "General answer",
                AnswerResolution.ANSWERED,
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL,
                        "Dependency injection supplies dependencies from outside.",
                        List.of(),
                        List.of())));
    }

    private ConversationAnswerRequest request(String question) {
        return request(question, new ConversationAnswerContextRequest(
                null,
                null,
                AudienceRole.INTERVIEWER,
                AnswerRequestSource.AGENT_PAGE));
    }

    private ConversationAnswerRequest request(
            String question,
            ConversationAnswerContextRequest context
    ) {
        return new ConversationAnswerRequest(
                "turn-1", question, List.of(), context);
    }

    private static final class Fixture {
        private final ConversationalAgentRuntime runtime;
        private final ConversationIntentRouter router;
        private final ConversationalModelPort modelPort;
        private final ConversationDraftValidator validator;
        private final PortfolioIntelligence intelligence;

        private Fixture(
                ConversationalAgentRuntime runtime,
                ConversationIntentRouter router,
                ConversationalModelPort modelPort,
                ConversationDraftValidator validator,
                PortfolioIntelligence intelligence
        ) {
            this.runtime = runtime;
            this.router = router;
            this.modelPort = modelPort;
            this.validator = validator;
            this.intelligence = intelligence;
        }
    }
}
