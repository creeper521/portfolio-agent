package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.adapter.observability.LoggingConversationDecisionPublisher;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
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
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.PortfolioRecommendationContextRequest;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioClarification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskResolver;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationalAgentRuntimeTest {

    @Test
    void explicitRecommendationUsesHardRoutingEvenWhenTheProviderIsDisabled() {
        RuntimeFixture fixture = fixture(false);
        PortfolioRecommendation recommendation = recommendation();
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "推荐作品",
                PortfolioTaskMode.RECOMMENDATION,
                1.0d,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null);
        when(fixture.taskResolver.matchesDeterministicRule(any())).thenReturn(true);
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(new PortfolioIntelligenceResult(
                PortfolioTaskMode.RECOMMENDATION,
                List.of(),
                List.of(),
                recommendation,
                null,
                false,
                null));

        ConversationAnswerResult result = fixture.runtime.answer(request("推荐作品"));

        assertThat(result.getPortfolioRecommendation()).isSameAs(recommendation);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getAnswerSource())
                .isEqualTo(com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL);
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("portfolio.intelligence.completed");
            assertThat(event.getFields()).containsOnlyKeys(
                    "task.mode",
                    "subject.count",
                    "evidence.count",
                    "recommendation.count",
                    "context.present",
                    "validation.result",
                    "duration.bucket");
            assertThat(event.getFields())
                    .containsEntry("task.mode", "RECOMMENDATION")
                    .containsEntry("context.present", false)
                    .containsEntry("recommendation.count", 1)
                    .containsEntry("validation.result", "ACCEPTED");
            assertThat(event.getFields().toString()).doesNotContain("鎺ㄨ崘浣滃搧");
        });
        verify(fixture.router).routeBoundary(any(), any(), any(), eq(true));
        verifyNoInteractions(fixture.modelPort);
    }

    @Test
    void timeSensitiveBoundaryRunsBeforeAnyPortfolioHardRoute() {
        RuntimeFixture fixture = fixture(false);
        ConversationRoute boundary = new ConversationRoute(
                ConversationIntent.TIME_SENSITIVE,
                ConversationAnswerScope.GENERAL,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
        when(fixture.router.routeBoundary(any(), any(), any(), eq(true)))
                .thenReturn(boundary);
        when(fixture.taskResolver.matchesDeterministicRule(any())).thenReturn(true);

        ConversationAnswerResult result = fixture.runtime.answer(request("最新推荐作品"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.TIME_SENSITIVE);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        verifyNoInteractions(fixture.portfolioIntelligence, fixture.modelPort);
    }

    @Test
    void semanticUnsafeBoundaryCanPreemptADeterministicPortfolioRule() {
        RuntimeFixture fixture = fixture(true);
        ConversationRoute boundary = new ConversationRoute(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                ConversationAnswerScope.CONVERSATION,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
        when(fixture.taskResolver.matchesDeterministicRule(any())).thenReturn(true);
        when(fixture.router.routeBoundary(any(), any(), any(), eq(true)))
                .thenReturn(boundary);

        ConversationAnswerResult result = fixture.runtime.answer(
                request("推荐一种绕过访问控制的办法"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.UNSUPPORTED_OR_UNSAFE);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.REJECTED);
        verifyNoInteractions(fixture.portfolioIntelligence, fixture.modelPort);
    }

    @Test
    void refinementPassesTheCompleteRecommendationContextToTheResolver() {
        RuntimeFixture fixture = fixture(false);
        PortfolioRecommendationContext expectedContext = recommendation().getContext();
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "调整推荐",
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                1.0d,
                PortfolioConditions.empty(),
                expectedContext,
                null);
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(new PortfolioIntelligenceResult(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                List.of(),
                List.of(),
                recommendation(),
                null,
                false,
                null));

        fixture.runtime.answer(requestWithRecommendationContext());

        ArgumentCaptor<PortfolioRecommendationContext> contextCaptor =
                ArgumentCaptor.forClass(PortfolioRecommendationContext.class);
        verify(fixture.taskResolver).resolve(any(), any(), contextCaptor.capture());
        assertThat(contextCaptor.getValue()).isEqualTo(expectedContext);
        verify(fixture.router).routeBoundary(any(), any(), any(), eq(true));
        verifyNoInteractions(fixture.modelPort);
    }

    @Test
    void projectSlugIsResolvedToAStableSubjectConstraintBeforeRetrieval() {
        RuntimeFixture fixture = fixture(false);
        when(fixture.knowledgeGateway.getContent()).thenReturn(contentWithProject());
        PortfolioTask unresolved = new PortfolioTask(
                "turn-1", "How was this verified?", PortfolioTaskMode.FACT_LOOKUP,
                1.0d, PortfolioConditions.empty(), null, null);
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(unresolved);
        when(fixture.portfolioIntelligence.resolve(any())).thenReturn(
                PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                        "Which verification detail matters most?", "facet")));

        fixture.runtime.answer(requestForProject("project-one"));

        ArgumentCaptor<PortfolioTask> taskCaptor = ArgumentCaptor.forClass(PortfolioTask.class);
        verify(fixture.portfolioIntelligence).resolve(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getSubjectId()).isEqualTo("stable-project-1");
    }

    @Test
    void outerPortfolioRouteTransfersToIntelligenceBeforeLegacyGroundingAndModel() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "Explain the strongest implementation detail",
                PortfolioTaskMode.FACT_LOOKUP,
                0.91d,
                PortfolioConditions.empty(),
                null,
                null);
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "passage-new-seam",
                "project-1",
                "claim-new-seam",
                "Material returned only by PortfolioIntelligence",
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-new-seam", "Public evidence", "APPROVED")));
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(portfolioRoute());
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(),
                List.of(passage),
                null,
                null,
                "intelligence-v2",
                false,
                null));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Explain the strongest implementation detail"));

        assertThat(result.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getContent()).isEqualTo(
                    "Material returned only by PortfolioIntelligence");
            assertThat(block.getClaimIds()).containsExactly("claim-new-seam");
            assertThat(block.getEvidenceIds()).containsExactly("evidence-new-seam");
        });
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getContentVersion()).isEqualTo("intelligence-v2");
        verifyNoInteractions(fixture.groundingAssembler, fixture.toolService, fixture.modelPort);
    }

    @Test
    void hybridRouteUsesIntelligenceGroundingAndPreservesHybridModelResult() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Relate this implementation to the general pattern",
                PortfolioTaskMode.FACT_LOOKUP, 0.91d,
                PortfolioConditions.empty(), null, null);
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "passage-hybrid",
                "project-1",
                "claim-hybrid",
                "Unified intelligence material",
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-hybrid", "Public evidence", "APPROVED")));
        PortfolioIntelligenceResult intelligenceResult = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(retrievedSubject()),
                List.of(passage),
                null,
                null,
                "intelligence-v3",
                false,
                null);
        ConversationDraft draft = new ConversationDraft(
                "Hybrid explanation", AnswerResolution.ANSWERED, List.of());
        com.portfolio.agent.answer.domain.ConversationAnswerBlock acceptedBlock =
                new com.portfolio.agent.answer.domain.ConversationAnswerBlock(
                        ConversationSourceScope.PORTFOLIO,
                        "Validated hybrid explanation",
                        List.of("claim-hybrid"),
                        List.of("evidence-hybrid"));
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(hybridRoute());
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(intelligenceResult);
        when(fixture.modelPort.generate(any(), any(), any(), any()))
                .thenReturn(ConversationModelResult.success(draft));
        when(fixture.draftValidator.validate(any(), any(), any()))
                .thenReturn(ConversationDraftValidationResult.valid(
                        draft, List.of(acceptedBlock)));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Relate this implementation to the general pattern"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.HYBRID);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.HYBRID);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.MODEL);
        assertThat(result.getContentVersion()).isEqualTo("intelligence-v3");
        ArgumentCaptor<PortfolioGroundingContext> groundingCaptor =
                ArgumentCaptor.forClass(PortfolioGroundingContext.class);
        verify(fixture.modelPort).generate(any(), any(), any(), groundingCaptor.capture());
        assertThat(groundingCaptor.getValue().getClaims()).singleElement()
                .satisfies(claim -> {
                    assertThat(claim.getId()).isEqualTo("claim-hybrid");
                    assertThat(claim.getStatement()).isEqualTo("Unified intelligence material");
                });
        verifyNoInteractions(fixture.groundingAssembler, fixture.toolService);
    }

    @Test
    void hybridProviderFailureFallsBackToDeterministicIntelligenceMaterial() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Relate implementation and theory", PortfolioTaskMode.FACT_LOOKUP,
                0.91d, PortfolioConditions.empty(), null, null);
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "passage-hybrid", "project-1", "claim-hybrid",
                "Safe deterministic intelligence fallback",
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-hybrid", "Public evidence", "APPROVED")));
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(hybridRoute());
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(
                new PortfolioIntelligenceResult(
                        PortfolioTaskMode.FACT_LOOKUP,
                        List.of(retrievedSubject()),
                        List.of(passage),
                        null,
                        null,
                        "intelligence-v3",
                        false,
                        null));
        when(fixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(ConversationModelFailureCode.PROVIDER_ERROR));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Relate implementation and theory"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.HYBRID);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.PORTFOLIO);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getNoticeCode()).isEqualTo("MODEL_UNAVAILABLE_FALLBACK");
        assertThat(result.getBlocks()).singleElement()
                .satisfies(block -> {
                    assertThat(block.getSourceScope()).isEqualTo(ConversationSourceScope.PORTFOLIO);
                    assertThat(block.getContent())
                            .isEqualTo("Safe deterministic intelligence fallback");
                });
        verifyNoInteractions(fixture.groundingAssembler, fixture.toolService);
    }

    @Test
    void hybridClarificationReportsPortfolioScopeForPortfolioOnlyBlock() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Relate this work to a general pattern",
                PortfolioTaskMode.CLARIFICATION_REQUIRED, 0.0d,
                PortfolioConditions.empty(), null, null);
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(hybridRoute());
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(
                PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                        "Which implementation detail should be related?", "facet")));

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Relate this work to a general pattern"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.HYBRID);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.PORTFOLIO);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getAnswerSource())
                .isEqualTo(com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL);
        assertThat(result.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getSourceScope()).isEqualTo(ConversationSourceScope.PORTFOLIO);
            assertThat(block.getContent())
                    .isEqualTo("Which implementation detail should be related?");
        });
        verifyNoInteractions(fixture.modelPort, fixture.draftValidator);
    }

    @Test
    void hybridWithoutEvidenceReportsPortfolioScopeAndDeterministicMetadata() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Relate missing evidence to theory", PortfolioTaskMode.FACT_LOOKUP,
                0.91d, PortfolioConditions.empty(), null, null);
        PortfolioIntelligenceResult intelligenceResult = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(retrievedSubject()),
                List.of(),
                null,
                null,
                "intelligence-empty",
                false,
                null);
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(hybridRoute());
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(intelligenceResult);

        ConversationAnswerResult result = fixture.runtime.answer(
                request("Relate missing evidence to theory"));

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.HYBRID);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.PORTFOLIO);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getAnswerSource())
                .isEqualTo(com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL);
        assertThat(result.getContentVersion()).isEqualTo("intelligence-empty");
        assertThat(result.getBlocks()).singleElement()
                .extracting(block -> block.getSourceScope())
                .isEqualTo(ConversationSourceScope.PORTFOLIO);
        verifyNoInteractions(fixture.modelPort, fixture.draftValidator);
    }

    @Test
    void clarificationReturnsExactlyOneCriticalQuestionWithoutCallingTheModel() {
        RuntimeFixture fixture = fixture(true);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "recommend", PortfolioTaskMode.CLARIFICATION_REQUIRED,
                0.0d, PortfolioConditions.empty(), null, null);
        when(fixture.taskResolver.matchesDeterministicRule(any())).thenReturn(true);
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(
                PortfolioIntelligenceResult.clarification(
                        new PortfolioClarification(
                                "Which audience should this recommendation target?",
                                "audienceRole")));

        ConversationAnswerResult result = fixture.runtime.answer(request("recommend"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.getBlocks()).singleElement()
                .extracting(block -> block.getContent())
                .isEqualTo("Which audience should this recommendation target?");
        assertThat(result.getPortfolioRecommendation()).isNull();
        verifyNoInteractions(fixture.modelPort);
    }

    @Test
    void enabledProviderCannotReplaceOrReorderAnEmptyStructuredRecommendation() {
        RuntimeFixture fixture = fixture(true);
        PortfolioRecommendation source = emptyRecommendation();
        PortfolioTask task = new PortfolioTask(
                "turn-1", "recommend", PortfolioTaskMode.RECOMMENDATION,
                1.0d,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null);
        when(fixture.taskResolver.matchesDeterministicRule(any())).thenReturn(true);
        when(fixture.taskResolver.resolve(any(), any(), any())).thenReturn(task);
        when(fixture.portfolioIntelligence.resolve(task)).thenReturn(new PortfolioIntelligenceResult(
                PortfolioTaskMode.RECOMMENDATION,
                List.of(),
                List.of(),
                source,
                null,
                true,
                "NO_MATCHING_PORTFOLIO"));

        ConversationAnswerResult result = fixture.runtime.answer(request("recommend"));

        assertThat(result.getPortfolioRecommendation()).isSameAs(source);
        assertThat(result.getPortfolioRecommendation().getItems()).isEmpty();
        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getNoticeCode()).isEqualTo("NO_MATCHING_PORTFOLIO");
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        verifyNoInteractions(fixture.modelPort);
    }

    @Test
    void publishesDecisionForProviderDisabledFallback() {
        RuntimeFixture fixture = fixture(false);

        ConversationAnswerResult result = fixture.runtime.answer(request("hello"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.CONVERSATION);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.CONVERSATION);
        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getSuggestedQuestions()).hasSize(3);
        assertThat(result.getProgress()).isNotNull();
        verify(fixture.router).routeBoundary(any(), any(), any(), eq(false));
        verifyNoInteractions(fixture.modelPort);
        assertPublishedDecision(fixture, result);
    }

    @Test
    void publishesDecisionForUnknownSubject() {
        RuntimeFixture fixture = fixture(true);

        ConversationAnswerResult result = fixture.runtime.answer(requestForUnknownCase());

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.PORTFOLIO_GROUNDED);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.PORTFOLIO);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getSuggestedQuestions()).hasSize(3);
        assertThat(result.getProgress().getStage())
                .isEqualTo(com.portfolio.agent.answer.domain.ConversationGuidanceStage.EXPLORE_OTHERS);
        assertThat(result.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getSourceScope()).isEqualTo(ConversationSourceScope.PORTFOLIO);
            assertThat(block.getClaimIds()).isEmpty();
            assertThat(block.getEvidenceIds()).isEmpty();
        });
        verify(fixture.router).routeBoundary(any(), any(), any(), eq(true));
        verifyNoInteractions(fixture.toolService, fixture.modelPort);
        assertPublishedDecision(fixture, result);
    }

    @Test
    void publishesDecisionForModelSuccess() {
        RuntimeFixture fixture = readyForGeneration();
        when(fixture.router.route(any(), any(), any())).thenReturn(generalRoute());
        ConversationDraft draft = new ConversationDraft(
                "Model answer", AnswerResolution.ANSWERED, List.of());
        when(fixture.modelPort.generate(any(), any(), any(), any()))
                .thenReturn(ConversationModelResult.success(draft));
        when(fixture.draftValidator.validate(any(), any(), any()))
                .thenReturn(ConversationDraftValidationResult.valid(draft, List.of()));
        ConversationAnswerResult result = fixture.runtime.answer(request("Explain validation"));

        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getSuggestedQuestions()).hasSize(3);
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.validation.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.DEBUG);
            assertThat(event.getFields())
                    .containsEntry("validation.accepted", true)
                    .containsEntry("failure.code", "NONE")
                    .containsOnlyKeys(
                            "validation.accepted",
                            "failure.code",
                            "duration.bucket");
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
        assertPublishedDecision(fixture, result);
    }

    @Test
    void publishesDecisionForUnsafeIntent() {
        RuntimeFixture fixture = fixture(true);
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(new ConversationRoute(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                ConversationAnswerScope.CONVERSATION,
                1.0,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false));

        ConversationAnswerResult result = fixture.runtime.answer(request("token"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.REJECTED);
        assertThat(result.getSuggestedQuestions()).hasSize(3);
        assertPublishedDecision(fixture, result);
    }

    @Test
    void publishesDecisionForProviderFailureFallback() {
        RuntimeFixture fixture = readyForGeneration();
        when(fixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(ConversationModelFailureCode.PROVIDER_ERROR));

        ConversationAnswerResult result = fixture.runtime.answer(request("Explain validation"));

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getSuggestedQuestions()).hasSize(3);
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry("fallback.trigger", "PROVIDER_FAILURE")
                    .containsEntry(
                            "failure.code",
                            "PROVIDER_CONNECTION_FAILED")
                    .containsOnlyKeys("fallback.trigger", "failure.code");
        });
        assertPublishedDecision(fixture, result);
    }

    @Test
    void publishesDecisionForValidationFailureFallback() {
        RuntimeFixture fixture = readyForGeneration();
        ConversationDraft draft = new ConversationDraft(
                "Invalid answer", AnswerResolution.ANSWERED, List.of());
        when(fixture.modelPort.generate(any(), any(), any(), any()))
                .thenReturn(ConversationModelResult.success(draft));
        when(fixture.draftValidator.validate(any(), any(), any()))
                .thenReturn(ConversationDraftValidationResult.invalid("INVALID_DRAFT_SHAPE"));

        ConversationAnswerResult result = fixture.runtime.answer(request("Explain validation"));

        assertThat(result.isDegraded()).isTrue();
        assertThat(fixture.events).hasSize(2);
        assertThat(fixture.events.get(0)).satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.validation.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry("validation.accepted", false)
                    .containsEntry("failure.code", "INVALID_DRAFT_SHAPE")
                    .containsOnlyKeys(
                            "validation.accepted",
                            "failure.code",
                            "duration.bucket");
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
        assertThat(fixture.events.get(1)).satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry("fallback.trigger", "VALIDATION_REJECTED")
                    .containsEntry("failure.code", "PROVIDER_DRAFT_REJECTED")
                    .containsOnlyKeys("fallback.trigger", "failure.code");
        });
        assertPublishedDecision(fixture, result);
    }

    @Test
    void validationExceptionSelectsExactlyOneTypedFallbackEvent() {
        RuntimeFixture fixture = readyForGeneration();
        ConversationDraft draft = new ConversationDraft(
                "Untrusted answer", AnswerResolution.ANSWERED, List.of());
        when(fixture.modelPort.generate(any(), any(), any(), any()))
                .thenReturn(ConversationModelResult.success(draft));
        when(fixture.draftValidator.validate(any(), any(), any()))
                .thenThrow(new IllegalStateException("sensitive validator detail"));

        ConversationAnswerResult result =
                fixture.runtime.answer(request("Explain validation"));

        assertThat(result.isDegraded()).isTrue();
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry(
                            "fallback.trigger",
                            "VALIDATION_EXCEPTION")
                    .containsEntry(
                            "failure.code",
                            "PROVIDER_DRAFT_REJECTED")
                    .containsOnlyKeys("fallback.trigger", "failure.code");
        });
        assertPublishedDecision(fixture, result);
    }

    @Test
    void preservesResultWhenDecisionPublisherFails() {
        RuntimeFixture expectedFixture = fixture(false);
        RuntimeFixture throwingFixture = fixture(false);
        doThrow(new RuntimeException("diagnostics unavailable"))
                .when(throwingFixture.decisionPublisher)
                .publish(any());

        ConversationAnswerResult expected = expectedFixture.runtime.answer(request("hello"));
        ConversationAnswerResult actual = throwingFixture.runtime.answer(request("hello"));

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void preservesFallbackResultWhenDiagnosticPublisherFails() {
        DiagnosticEventPublisher throwingPublisher = event -> {
            throw new IllegalStateException("diagnostics unavailable");
        };
        RuntimeFixture expectedFixture = readyForGeneration();
        RuntimeFixture throwingFixture = readyForGeneration(throwingPublisher);
        when(expectedFixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(ConversationModelFailureCode.TIMEOUT));
        when(throwingFixture.modelPort.generate(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(ConversationModelFailureCode.TIMEOUT));

        ConversationAnswerResult expected =
                expectedFixture.runtime.answer(request("Explain validation"));
        ConversationAnswerResult actual =
                throwingFixture.runtime.answer(request("Explain validation"));

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void publishesOneRouteEventAndOneCompletedEventForV2Request() {
        List<DiagnosticEvent> events = new ArrayList<>();
        DiagnosticEventPublisher diagnosticPublisher = events::add;
        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        when(knowledgeGateway.getContent()).thenReturn(
                new RuntimeAnswerContent("v1", "hash", List.of()));
        ConversationWindowManager windowManager = mock(ConversationWindowManager.class);
        when(windowManager.prepare(any(), any())).thenReturn(window());
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        ConversationIntentRouter router = new ConversationIntentRouter(
                modelPort, 0.65, diagnosticPublisher);
        DynamicQuestionService questionService =
                mock(DynamicQuestionService.class);
        when(questionService.generate(
                any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(suggestions());
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                knowledgeGateway,
                windowManager,
                router,
                mock(PortfolioGroundingAssembler.class),
                mock(ConversationToolService.class),
                modelPort,
                mock(ConversationDraftValidator.class),
                questionService,
                new DeterministicConversationFallback(),
                new ConversationProviderAccess(true),
                new ConversationSubjectGuard(),
                mock(PortfolioTaskResolver.class),
                mock(PortfolioIntelligence.class),
                new PortfolioIntelligenceAnswerAssembler(),
                new ConversationProgressClassifier(),
                new LoggingConversationDecisionPublisher(diagnosticPublisher),
                diagnosticPublisher);

        ConversationAnswerResult result = runtime.answer(request("token"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.REJECTED);
        assertThat(events).hasSize(2);
        assertThat(events).filteredOn(event -> event.getName().equals("agent.route.decided"))
                .singleElement();
        assertThat(events).filteredOn(event -> event.getName().equals("agent.request.completed"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFields()).containsOnlyKeys(
                            "content.version",
                            "conversation.intent",
                            "answer.scope",
                            "answer.resolution",
                            "answer.degraded",
                            "generation.mode",
                            "answer.source",
                            "duration.bucket");
                    assertThat(event.getFields())
                            .containsEntry("conversation.intent", "UNSUPPORTED_OR_UNSAFE")
                            .containsEntry("answer.resolution", "REJECTED")
                            .containsEntry("answer.degraded", false)
                            .containsEntry("generation.mode", "DETERMINISTIC")
                            .containsEntry("answer.source", "NONE");
                });
        verifyNoInteractions(modelPort);
    }

    private RuntimeFixture readyForGeneration() {
        return readyForGeneration(null);
    }

    private RuntimeFixture readyForGeneration(
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        RuntimeFixture fixture = diagnosticEventPublisher == null
                ? fixture(true)
                : fixture(true, diagnosticEventPublisher, List.of());
        when(fixture.windowManager.prepare(any(), any())).thenReturn(window());
        when(fixture.router.route(any(), any(), any())).thenReturn(generalRoute());
        when(fixture.groundingAssembler.assemble(any(), any(), any()))
                .thenReturn(PortfolioGroundingContext.empty());
        when(fixture.toolService.enrich(any(), any(), any(), any(), any()))
                .thenReturn(PortfolioGroundingContext.empty());
        return fixture;
    }

    private RuntimeFixture fixture(boolean providerAllowed) {
        List<DiagnosticEvent> events = new ArrayList<>();
        return fixture(providerAllowed, events::add, events);
    }

    private void assertDurationBucket(Object value) {
        assertThat(value).isInstanceOf(String.class);
        assertThat(java.util.Arrays.stream(DurationBucket.values())
                .map(DurationBucket::name))
                .contains((String) value);
    }

    private RuntimeFixture fixture(
            boolean providerAllowed,
            DiagnosticEventPublisher diagnosticEventPublisher,
            List<DiagnosticEvent> events
    ) {
        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        when(knowledgeGateway.getContent()).thenReturn(
                new RuntimeAnswerContent("v1", "hash", List.of()));
        ConversationWindowManager windowManager = mock(ConversationWindowManager.class);
        ConversationIntentRouter router = mock(ConversationIntentRouter.class);
        PortfolioGroundingAssembler groundingAssembler = mock(PortfolioGroundingAssembler.class);
        ConversationToolService toolService = mock(ConversationToolService.class);
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        ConversationDraftValidator draftValidator = mock(ConversationDraftValidator.class);
        DynamicQuestionService questionService = mock(DynamicQuestionService.class);
        when(questionService.generate(
                any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(suggestions());
        ConversationDecisionPublisher decisionPublisher = mock(ConversationDecisionPublisher.class);
        PortfolioTaskResolver taskResolver = mock(PortfolioTaskResolver.class);
        PortfolioIntelligence portfolioIntelligence = mock(PortfolioIntelligence.class);
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                knowledgeGateway,
                windowManager,
                router,
                groundingAssembler,
                toolService,
                modelPort,
                draftValidator,
                questionService,
                new DeterministicConversationFallback(),
                new ConversationProviderAccess(providerAllowed),
                new ConversationSubjectGuard(),
                taskResolver,
                portfolioIntelligence,
                new PortfolioIntelligenceAnswerAssembler(),
                new ConversationProgressClassifier(),
                decisionPublisher,
                diagnosticEventPublisher);
        return new RuntimeFixture(
                runtime, knowledgeGateway, windowManager, router, groundingAssembler, toolService, modelPort,
                draftValidator, questionService, taskResolver, portfolioIntelligence,
                decisionPublisher, events);
    }

    private void assertPublishedDecision(
            RuntimeFixture fixture,
            ConversationAnswerResult result
    ) {
        ArgumentCaptor<com.portfolio.agent.answer.domain.ConversationDecision> captor =
                ArgumentCaptor.forClass(com.portfolio.agent.answer.domain.ConversationDecision.class);
        verify(fixture.decisionPublisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue().getResolution()).isEqualTo(result.getResolution());
        assertThat(captor.getValue().isDegraded()).isEqualTo(result.isDegraded());
        assertThat(captor.getValue().getGenerationMode())
                .isEqualTo(result.getGenerationMode());
        assertThat(captor.getValue().getAnswerSource())
                .isEqualTo(result.getAnswerSource());
        assertThat(captor.getValue().getDurationBucket()).isNotNull();
    }

    private ConversationRoute generalRoute() {
        return new ConversationRoute(
                ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GENERAL,
                1.0,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private ConversationRoute portfolioRoute() {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0,
                "project-1",
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private ConversationWindow window() {
        return new ConversationWindow(null, List.of(), 0);
    }

    private static List<ConversationSuggestedQuestion> suggestions() {
        return List.of(
                new ConversationSuggestedQuestion(
                        "继续了解项目背景？",
                        "project-1",
                        null,
                        PortfolioKnowledgeFacet.OVERVIEW),
                new ConversationSuggestedQuestion(
                        "继续了解实现方案？",
                        "project-1",
                        null,
                        PortfolioKnowledgeFacet.IMPLEMENTATION),
                new ConversationSuggestedQuestion(
                        "继续了解验证结果？",
                        "project-1",
                        null,
                        PortfolioKnowledgeFacet.VERIFICATION));
    }

    private ConversationRoute hybridRoute() {
        return new ConversationRoute(
                ConversationIntent.HYBRID,
                ConversationAnswerScope.HYBRID,
                1.0,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject
            retrievedSubject() {
        return new com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject(
                "project-1",
                "PROJECT",
                "Project one",
                "Public summary",
                "/projects/project-one",
                "BACKEND",
                Set.of("JAVA"));
    }

    private PortfolioRecommendation recommendation() {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_" + "a".repeat(64),
                "portfolio-v2",
                "BACKEND",
                "INTERVIEWER",
                Set.of("JAVA"),
                2,
                List.of("project-1"));
        return new PortfolioRecommendation(
                context.getRecommendationBatchId(),
                context,
                List.of(new PortfolioRecommendationItem(
                        "project-1",
                        "Project one",
                        "/projects/project-one",
                        List.of("Matches Java backend"),
                        List.of("evidence-1"))),
                List.of("audienceRole", "requestedSize"),
                List.of());
    }

    private PortfolioRecommendation emptyRecommendation() {
        PortfolioRecommendation recommendation = recommendation();
        return new PortfolioRecommendation(
                recommendation.getRecommendationBatchId(),
                recommendation.getContext(),
                List.of(),
                recommendation.getSatisfiedConstraints(),
                recommendation.getUnsatisfiedConstraints());
    }

    private ConversationAnswerRequest request(String question) {
        return new ConversationAnswerRequest(
                "turn-1",
                question,
                List.of(),
                new ConversationAnswerContextRequest(
                        null,
                        null,
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.AGENT_PAGE));
    }

    private ConversationAnswerRequest requestForUnknownCase() {
        return new ConversationAnswerRequest(
                "turn-unknown-case",
                "Tell me about this case",
                List.of(),
                new ConversationAnswerContextRequest(
                        null,
                        "unknown-case",
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.CASE));
    }

    private ConversationAnswerRequest requestForProject(String projectSlug) {
        return new ConversationAnswerRequest(
                "turn-1",
                "How was this verified?",
                List.of(),
                new ConversationAnswerContextRequest(
                        projectSlug,
                        null,
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.PROJECT));
    }

    private RuntimeAnswerContent contentWithProject() {
        AnswerKnowledge project = new AnswerKnowledge(
                AnswerSubjectType.PROJECT,
                "stable-project-1",
                "project-one",
                "Project one",
                "Public summary",
                "Public background",
                List.of(),
                "Public solution",
                List.of(),
                List.of(),
                "Public outcome",
                "Public handoff",
                "PUBLISHED",
                "BACKEND",
                Set.of("JAVA"),
                List.of(),
                List.of(),
                List.of());
        return new RuntimeAnswerContent("v1", "hash", List.of(project));
    }

    private ConversationAnswerRequest requestWithRecommendationContext() {
        PortfolioRecommendationContext context = recommendation().getContext();
        PortfolioRecommendationContextRequest contextRequest =
                new PortfolioRecommendationContextRequest(
                        context.getRecommendationBatchId(),
                        context.getContentVersion(),
                        context.getCareerTrack(),
                        context.getAudienceRole(),
                        context.getCapabilityCodes(),
                        context.getRequestedSize(),
                        context.getSelectedPortfolioIds());
        return new ConversationAnswerRequest(
                "turn-1",
                "调整推荐",
                List.of(),
                new ConversationAnswerContextRequest(
                        null,
                        null,
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.AGENT_PAGE,
                        List.of(),
                        contextRequest));
    }

    private static final class RuntimeFixture {
        private final ConversationalAgentRuntime runtime;
        private final PortfolioKnowledgeGateway knowledgeGateway;
        private final ConversationWindowManager windowManager;
        private final ConversationIntentRouter router;
        private final PortfolioGroundingAssembler groundingAssembler;
        private final ConversationToolService toolService;
        private final ConversationalModelPort modelPort;
        private final ConversationDraftValidator draftValidator;
        private final DynamicQuestionService questionService;
        private final PortfolioTaskResolver taskResolver;
        private final PortfolioIntelligence portfolioIntelligence;
        private final ConversationDecisionPublisher decisionPublisher;
        private final List<DiagnosticEvent> events;

        private RuntimeFixture(
                ConversationalAgentRuntime runtime,
                PortfolioKnowledgeGateway knowledgeGateway,
                ConversationWindowManager windowManager,
                ConversationIntentRouter router,
                PortfolioGroundingAssembler groundingAssembler,
                ConversationToolService toolService,
                ConversationalModelPort modelPort,
                ConversationDraftValidator draftValidator,
                DynamicQuestionService questionService,
                PortfolioTaskResolver taskResolver,
                PortfolioIntelligence portfolioIntelligence,
                ConversationDecisionPublisher decisionPublisher,
                List<DiagnosticEvent> events
        ) {
            this.runtime = runtime;
            this.knowledgeGateway = knowledgeGateway;
            this.windowManager = windowManager;
            this.router = router;
            this.groundingAssembler = groundingAssembler;
            this.toolService = toolService;
            this.modelPort = modelPort;
            this.draftValidator = draftValidator;
            this.questionService = questionService;
            this.taskResolver = taskResolver;
            this.portfolioIntelligence = portfolioIntelligence;
            this.decisionPublisher = decisionPublisher;
            this.events = events;
        }
    }
}
