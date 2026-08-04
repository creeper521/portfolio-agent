package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDecision;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.PortfolioRecommendationContextRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ConversationalAgentRuntime {

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final ConversationWindowManager windowManager;
    private final ConversationIntentRouter intentRouter;
    private final PortfolioGroundingAssembler groundingAssembler;
    private final ConversationToolService toolService;
    private final ConversationalModelPort modelPort;
    private final ConversationDraftValidator draftValidator;
    private final DynamicQuestionService questionService;
    private final DeterministicConversationFallback fallback;
    private final ConversationProviderAccess providerAccess;
    private final PortfolioIntelligence portfolioIntelligence;
    private final PortfolioIntelligenceAnswerAssembler portfolioAnswerAssembler;
    private final ConversationProgressClassifier progressClassifier;
    private final ConversationDecisionPublisher decisionPublisher;
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public ConversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            ConversationWindowManager windowManager,
            ConversationIntentRouter intentRouter,
            PortfolioGroundingAssembler groundingAssembler,
            ConversationToolService toolService,
            ConversationalModelPort modelPort,
            ConversationDraftValidator draftValidator,
            DynamicQuestionService questionService,
            DeterministicConversationFallback fallback,
            ConversationProviderAccess providerAccess,
            PortfolioIntelligence portfolioIntelligence,
            PortfolioIntelligenceAnswerAssembler portfolioAnswerAssembler,
            ConversationProgressClassifier progressClassifier,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.windowManager = Objects.requireNonNull(windowManager, "windowManager");
        this.intentRouter = Objects.requireNonNull(intentRouter, "intentRouter");
        this.groundingAssembler = Objects.requireNonNull(groundingAssembler, "groundingAssembler");
        this.toolService = Objects.requireNonNull(toolService, "toolService");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.draftValidator = Objects.requireNonNull(draftValidator, "draftValidator");
        this.questionService = Objects.requireNonNull(questionService, "questionService");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.providerAccess = Objects.requireNonNull(providerAccess, "providerAccess");
        this.portfolioIntelligence = Objects.requireNonNull(
                portfolioIntelligence, "portfolioIntelligence");
        this.portfolioAnswerAssembler = Objects.requireNonNull(
                portfolioAnswerAssembler, "portfolioAnswerAssembler");
        this.progressClassifier = Objects.requireNonNull(
                progressClassifier, "progressClassifier");
        this.decisionPublisher = Objects.requireNonNull(decisionPublisher, "decisionPublisher");
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher, "diagnosticEventPublisher");
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request) {
        Objects.requireNonNull(request, "request");
        long startedAt = System.nanoTime();
        ConversationAnswerResult result = answerInternal(request);
        publishBestEffort(result, startedAt);
        return result;
    }

    private ConversationAnswerResult answerInternal(ConversationAnswerRequest request) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ConversationWindow window = windowManager.prepare(
                request.getMessages(), request.getQuestion());
        ConversationRoute globalRoute = intentRouter.routeBoundary(request.getQuestion());
        if (globalRoute != null) {
            return finalizeTurn(
                    fallback.answer(request, content, globalRoute),
                    content,
                    globalRoute,
                    window,
                    request,
                    false);
        }

        long portfolioStartedAt = System.nanoTime();
        PortfolioDecision decision = portfolioIntelligence.tryResolve(
                portfolioTurn(request, window));
        if (decision.getDisposition() != PortfolioDisposition.NOT_PORTFOLIO) {
            PortfolioIntelligenceResult material = decision.getMaterial().orElseThrow();
            publishPortfolioIntelligence(
                    material,
                    request.getContext().getRecommendationContext() != null,
                    portfolioStartedAt);
            ConversationAnswerResult result = portfolioAnswerAssembler.assemble(
                    request, content, decision);
            return finalizeTurn(
                    result,
                    content,
                    portfolioGuidanceRoute(request, !material.getSubjects().isEmpty()),
                    window,
                    request,
                    false);
        }
        return answerGeneral(request, content, window);
    }

    private ConversationAnswerResult answerGeneral(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationWindow window
    ) {
        ConversationRoute route = new ConversationRoute(
                ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GENERAL,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
        if (!providerAccess.isAllowed()) {
            return finalizeTurn(
                    fallback.capabilityUnavailable(request, content),
                    content,
                    route,
                    window,
                    request,
                    false);
        }
        PortfolioGroundingContext grounding = groundingAssembler.assemble(
                content, route, request.getQuestion());
        grounding = toolService.enrich(
                content, request.getQuestion(), window, route, grounding);
        ConversationModelResult<ConversationDraft> generated = modelPort.generate(
                request.getQuestion(), window, route, grounding);
        if (generated == null || !generated.isSuccessful()) {
            ConversationModelFailureCode failureCode = generated == null
                    ? ConversationModelFailureCode.INVALID_RESPONSE
                    : generated.getFailureCode();
            publishFallback(
                    FallbackTrigger.PROVIDER_FAILURE,
                    ProviderFailureCodeMapper.map(failureCode));
            return finalizeTurn(
                    fallback.capabilityUnavailable(request, content),
                    content,
                    route,
                    window,
                    request,
                    false);
        }
        ConversationDraftValidationResult validated = validateDraft(
                generated.getValue(), grounding);
        if (validated == null || !validated.isValid()) {
            return finalizeTurn(
                    fallback.capabilityUnavailable(request, content),
                    content,
                    route,
                    window,
                    request,
                    false);
        }
        ConversationAnswerResult base = new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GENERAL,
                validated.getResolution(),
                validated.getTitle(),
                validated.getAcceptedBlocks(),
                List.of(),
                false,
                GenerationMode.MODEL,
                null,
                null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING),
                null,
                AnswerConstructionMode.GENERAL_MODEL,
                AnswerIntentSource.GLOBAL,
                AnswerEvidenceState.NOT_REQUIRED);
        return finalizeTurn(base, content, route, window, request, false);
    }

    private ConversationDraftValidationResult validateDraft(
            ConversationDraft draft,
            PortfolioGroundingContext grounding
    ) {
        long startedAt = System.nanoTime();
        try {
            ConversationDraftValidationResult validated = draftValidator.validate(
                    draft, ConversationAnswerScope.GENERAL, grounding);
            publishValidation(validated, startedAt);
            if (!validated.isValid()) {
                publishFallback(
                        FallbackTrigger.VALIDATION_REJECTED,
                        ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            }
            return validated;
        } catch (RuntimeException exception) {
            publishFallback(
                    FallbackTrigger.VALIDATION_EXCEPTION,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return null;
        }
    }

    private PortfolioTurn portfolioTurn(
            ConversationAnswerRequest request,
            ConversationWindow window
    ) {
        return PortfolioTurn.builder(request.getTurnId(), request.getQuestion())
                .questionPresetId(request.getQuestionPresetId())
                .contractVersion(request.getContractVersion())
                .window(window)
                .projectSlug(request.getContext().getProjectSlug())
                .caseSlug(request.getContext().getCaseSlug())
                .recommendationContext(recommendationContext(request))
                .referenceContext(referenceContext(request))
                .audienceRole(request.getContext().getAudienceRole().name())
                .source(request.getContext().getSource().name())
                .build();
    }

    private PortfolioReferenceContext referenceContext(ConversationAnswerRequest request) {
        com.portfolio.agent.answer.dto.request.PortfolioReferenceContextRequest reference =
                request.getContext().getReferenceContext();
        if (reference == null) {
            return null;
        }
        return new PortfolioReferenceContext(
                reference.getPreviousContentVersion(),
                reference.getProjectSlugs(),
                reference.getCaseSlugs(),
                reference.getQuestionPresetId(),
                reference.getReferencedClaimIds(),
                reference.getSelectedSectionType(),
                reference.getFollowUpAction());
    }

    private PortfolioRecommendationContext recommendationContext(
            ConversationAnswerRequest request
    ) {
        PortfolioRecommendationContextRequest context =
                request.getContext().getRecommendationContext();
        if (context == null) {
            return null;
        }
        return new PortfolioRecommendationContext(
                context.getRecommendationBatchId(),
                context.getContentVersion(),
                context.getCareerTrack(),
                context.getAudienceRole(),
                context.getCapabilityCodes(),
                context.getRequestedSize(),
                context.getSelectedPortfolioIds());
    }

    private ConversationAnswerResult finalizeTurn(
            ConversationAnswerResult base,
            RuntimeAnswerContent content,
            ConversationRoute route,
            ConversationWindow window,
            ConversationAnswerRequest request,
            boolean forceExploreOthers
    ) {
        ConversationProgress progress = progressClassifier.classify(
                request.getContext().getCoveredTopics(),
                request.getQuestion(),
                route.getFacet());
        if (forceExploreOthers
                || (route.getProjectSlug() == null && route.getCaseSlug() == null)) {
            progress = new ConversationProgress(
                    progress.getCoveredTopics(),
                    ConversationGuidanceStage.EXPLORE_OTHERS);
        }
        List<ConversationSuggestedQuestion> suggestions = questionService.generate(
                content,
                route,
                window,
                base.getBlocks(),
                progress,
                request.getQuestion(),
                base.getConstructionMode() == AnswerConstructionMode.GENERAL_MODEL
                        || base.getConstructionMode() == AnswerConstructionMode.MODEL_GROUNDED);
        return base.withGuidance(suggestions, progress);
    }

    private ConversationRoute portfolioGuidanceRoute(
            ConversationAnswerRequest request,
            boolean hasResolvedSubject
    ) {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0d,
                hasResolvedSubject ? request.getContext().getProjectSlug() : null,
                hasResolvedSubject ? request.getContext().getCaseSlug() : null,
                progressClassifier.inferFacet(request.getQuestion()),
                false);
    }

    private void publishPortfolioIntelligence(
            PortfolioIntelligenceResult result,
            boolean contextPresent,
            long startedAt
    ) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        int recommendationCount = result.getPortfolioRecommendation() == null
                ? 0
                : result.getPortfolioRecommendation().getItems().size();
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "portfolio.intelligence.completed",
                            result.isDegraded() ? DiagnosticLevel.WARN : DiagnosticLevel.INFO)
                    .field("intent.source", result.getIntentSource())
                    .field("task.mode", result.getResolvedIntent())
                    .field("subject.count", result.getSubjects().size())
                    .field("evidence.count", result.getEvidence().size())
                    .field("recommendation.count", recommendationCount)
                    .field("context.present", contextPresent)
                    .field("degraded", result.isDegraded())
                    .field("duration.bucket", DurationBuckets.fromElapsedMillis(elapsedMillis))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private void publishBestEffort(ConversationAnswerResult result, long startedAt) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        try {
            decisionPublisher.publish(new ConversationDecision(
                    Instant.now(),
                    result.getContentVersion(),
                    result.getIntent(),
                    result.getAnswerScope(),
                    result.getResolution(),
                    result.isDegraded(),
                    result.getGenerationMode(),
                    result.getAnswerSource(),
                    DurationBuckets.fromElapsedMillis(elapsedMillis)));
        } catch (RuntimeException ignored) {
            // Observability is passive and must never change the visitor response.
        }
    }

    private void publishValidation(
            ConversationDraftValidationResult validation,
            long startedAt
    ) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        String failureCode = validation.isValid() ? "NONE" : validation.getFailureCode();
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "answer.validation.completed",
                            validation.isValid() ? DiagnosticLevel.DEBUG : DiagnosticLevel.WARN)
                    .field("validation.accepted", validation.isValid())
                    .field("failure.code", failureCode)
                    .field("duration.bucket", DurationBuckets.fromElapsedMillis(elapsedMillis))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private void publishFallback(
            FallbackTrigger fallbackTrigger,
            ProviderFailureCode failureCode
    ) {
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "answer.fallback.selected", DiagnosticLevel.WARN)
                    .field("fallback.trigger", fallbackTrigger)
                    .field("failure.code", failureCode)
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private enum FallbackTrigger {
        PROVIDER_FAILURE,
        VALIDATION_REJECTED,
        VALIDATION_EXCEPTION
    }
}
