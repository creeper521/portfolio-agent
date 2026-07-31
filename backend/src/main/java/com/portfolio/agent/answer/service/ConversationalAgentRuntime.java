package com.portfolio.agent.answer.service;

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
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.PortfolioRecommendationContextRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskRoutingDecision;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.intelligence.service.PortfolioTaskResolver;
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
    private final ConversationSubjectGuard subjectGuard;
    private final PortfolioTaskResolver portfolioTaskResolver;
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
            ConversationSubjectGuard subjectGuard,
            ConversationProgressClassifier progressClassifier,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this(
                knowledgeGateway,
                windowManager,
                intentRouter,
                groundingAssembler,
                toolService,
                modelPort,
                draftValidator,
                questionService,
                fallback,
                providerAccess,
                subjectGuard,
                null,
                null,
                null,
                progressClassifier,
                decisionPublisher,
                diagnosticEventPublisher);
    }

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
            ConversationSubjectGuard subjectGuard,
            PortfolioTaskResolver portfolioTaskResolver,
            PortfolioIntelligence portfolioIntelligence,
            PortfolioIntelligenceAnswerAssembler portfolioAnswerAssembler,
            ConversationProgressClassifier progressClassifier,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.windowManager = windowManager;
        this.intentRouter = intentRouter;
        this.groundingAssembler = groundingAssembler;
        this.toolService = toolService;
        this.modelPort = modelPort;
        this.draftValidator = draftValidator;
        this.questionService = questionService;
        this.fallback = fallback;
        this.providerAccess = providerAccess;
        this.subjectGuard = subjectGuard;
        this.portfolioTaskResolver = portfolioTaskResolver;
        this.portfolioIntelligence = portfolioIntelligence;
        this.portfolioAnswerAssembler = portfolioAnswerAssembler;
        this.progressClassifier = Objects.requireNonNull(
                progressClassifier,
                "progressClassifier");
        this.decisionPublisher = decisionPublisher;
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher,
                "diagnosticEventPublisher");
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request) {
        long startedAt = System.nanoTime();
        ConversationAnswerResult result = answerInternal(request);
        publishBestEffort(result, startedAt);
        return result;
    }

    private ConversationAnswerResult answerInternal(ConversationAnswerRequest request) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ConversationWindow window = windowManager.prepare(
                request.getMessages(), request.getQuestion());
        ConversationRoute boundary = intentRouter.routeBoundary(request.getQuestion());
        if (boundary != null) {
            ConversationAnswerResult base = fallback.answer(request, content, boundary);
            return finalizeTurn(
                    base,
                    content,
                    boundary,
                    window,
                    request,
                    false);
        }
        if (!subjectGuard.accepts(request.getContext(), content)) {
            ConversationAnswerResult base =
                    fallback.unknownSubject(request, content);
            return finalizeTurn(
                    base,
                    content,
                    safeRoute(request, true),
                    window,
                    request,
                    true);
        }
        boolean deterministicPortfolioRule = hasPortfolioIntelligence()
                && portfolioTaskResolver.matchesDeterministicRule(request.getQuestion());
        boolean portfolioHardRoute = usesPortfolioIntelligence(
                request, deterministicPortfolioRule);
        if (portfolioHardRoute) {
            PortfolioTaskRoutingDecision decision = portfolioTaskResolver.route(
                    request.getTurnId(),
                    request.getQuestion(),
                    recommendationContext(request),
                    providerAccess.isAllowed());
            if (decision.getBoundaryIntent() != null) {
                boundary = portfolioBoundary(decision.getBoundaryIntent());
                ConversationAnswerResult base = fallback.answer(request, content, boundary);
                return finalizeTurn(
                        base,
                        content,
                        boundary,
                        window,
                        request,
                        false);
            }
            return answerWithPortfolioIntelligence(
                    request,
                    content,
                    window,
                    safeRoute(request, false),
                    decision.getTask());
        }
        if (!providerAccess.isAllowed()) {
            ConversationAnswerResult base = fallback.answer(request, content);
            return finalizeTurn(
                    base,
                    content,
                    safeRoute(request, false),
                    window,
                    request,
                    false);
        }
        ConversationRoute route = intentRouter.route(content, window, request);
        ConversationRoute guidanceRoute = withRequestSubject(route, request);
        if (route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.TIME_SENSITIVE
                || route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            ConversationAnswerResult base =
                    fallback.answer(request, content, route);
            return finalizeTurn(
                    base,
                    content,
                    guidanceRoute,
                    window,
                    request,
                    false);
        }
        if (hasPortfolioIntelligence()
                && route.getIntent() == ConversationIntent.PORTFOLIO_GROUNDED) {
            return answerWithPortfolioIntelligence(
                    request, content, window, guidanceRoute, null);
        }
        if (hasPortfolioIntelligence() && route.getIntent() == ConversationIntent.HYBRID) {
            return answerHybridWithPortfolioIntelligence(
                    request, content, window, guidanceRoute);
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
            ConversationAnswerResult fallbackResult =
                    fallback.answer(request, content, route);
            publishFallback(
                    FallbackTrigger.PROVIDER_FAILURE,
                    ProviderFailureCodeMapper.map(failureCode));
            return finalizeTurn(
                    fallbackResult,
                    content,
                    guidanceRoute,
                    window,
                    request,
                    false);
        }
        long validationStartedAt = System.nanoTime();
        ConversationDraftValidationResult validated;
        try {
            validated = draftValidator.validate(
                    generated.getValue(),
                    route.getAnswerScope(),
                    grounding);
        } catch (RuntimeException exception) {
            ConversationAnswerResult fallbackResult =
                    fallback.answer(request, content, route);
            publishFallback(
                    FallbackTrigger.VALIDATION_EXCEPTION,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return finalizeTurn(
                    fallbackResult,
                    content,
                    guidanceRoute,
                    window,
                    request,
                    false);
        }
        publishValidation(validated, validationStartedAt);
        if (!validated.isValid()) {
            ConversationAnswerResult fallbackResult =
                    fallback.answer(request, content, route);
            publishFallback(
                    FallbackTrigger.VALIDATION_REJECTED,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return finalizeTurn(
                    fallbackResult,
                    content,
                    guidanceRoute,
                    window,
                    request,
                    false);
        }
        ConversationAnswerResult base = new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                route.getIntent(),
                route.getAnswerScope(),
                validated.getResolution(),
                validated.getTitle(),
                validated.getAcceptedBlocks(),
                List.of(),
                false,
                GenerationMode.MODEL,
                route.getAnswerScope() == ConversationAnswerScope.PORTFOLIO
                        || route.getAnswerScope() == ConversationAnswerScope.HYBRID
                        ? AnswerSource.RETRIEVAL
                        : null,
                null);
        return finalizeTurn(
                base,
                content,
                guidanceRoute,
                window,
                request,
                false);
    }

    private boolean usesPortfolioIntelligence(
            ConversationAnswerRequest request,
            boolean deterministicPortfolioRule) {
        if (!hasPortfolioIntelligence()) {
            return false;
        }
        return request.getContext().getRecommendationContext() != null
                || hasSubjectHint(request)
                || deterministicPortfolioRule;
    }

    private boolean hasSubjectHint(ConversationAnswerRequest request) {
        return hasText(request.getContext().getProjectSlug())
                || hasText(request.getContext().getCaseSlug());
    }

    private boolean hasPortfolioIntelligence() {
        return portfolioTaskResolver != null
                && portfolioIntelligence != null
                && portfolioAnswerAssembler != null;
    }

    private ConversationAnswerResult answerWithPortfolioIntelligence(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationWindow window,
            ConversationRoute guidanceRoute,
            PortfolioTask decidedTask) {
        PortfolioIntelligenceResult intelligenceResult = resolvePortfolioIntelligence(
                request, content, guidanceRoute, decidedTask);
        ConversationAnswerResult base = portfolioAnswerAssembler.assemble(
                request, content, intelligenceResult);
        return finalizeTurn(
                base,
                content,
                guidanceRoute,
                window,
                request,
                false);
    }

    private PortfolioIntelligenceResult resolvePortfolioIntelligence(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationRoute guidanceRoute) {
        return resolvePortfolioIntelligence(request, content, guidanceRoute, null);
    }

    private PortfolioIntelligenceResult resolvePortfolioIntelligence(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationRoute guidanceRoute,
            PortfolioTask decidedTask) {
        long startedAt = System.nanoTime();
        boolean contextPresent = request.getContext().getRecommendationContext() != null;
        PortfolioTask task = decidedTask == null
                ? portfolioTaskResolver.resolve(
                        request.getTurnId(),
                        request.getQuestion(),
                        recommendationContext(request))
                : decidedTask;
        task = withSubjectConstraint(task, content, guidanceRoute);
        PortfolioIntelligenceResult intelligenceResult = portfolioIntelligence.resolve(task);
        publishPortfolioIntelligence(intelligenceResult, contextPresent, startedAt);
        return intelligenceResult;
    }

    private ConversationAnswerResult answerHybridWithPortfolioIntelligence(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationWindow window,
            ConversationRoute route) {
        PortfolioIntelligenceResult intelligenceResult = resolvePortfolioIntelligence(
                request, content, route);
        ConversationAnswerResult deterministic = portfolioAnswerAssembler.assemble(
                request,
                content,
                intelligenceResult,
                ConversationIntent.HYBRID,
                ConversationAnswerScope.PORTFOLIO);
        if (intelligenceResult.getResolvedIntent()
                == com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode.CLARIFICATION_REQUIRED
                || intelligenceResult.getEvidence().isEmpty()) {
            return finalizeTurn(deterministic, content, route, window, request, false);
        }
        PortfolioGroundingContext grounding = portfolioAnswerAssembler.grounding(intelligenceResult);
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
                    hybridFallback(deterministic, intelligenceResult),
                    content, route, window, request, false);
        }
        long validationStartedAt = System.nanoTime();
        ConversationDraftValidationResult validated;
        try {
            validated = draftValidator.validate(
                    generated.getValue(), ConversationAnswerScope.HYBRID, grounding);
        } catch (RuntimeException exception) {
            publishFallback(
                    FallbackTrigger.VALIDATION_EXCEPTION,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return finalizeTurn(
                    hybridFallback(deterministic, intelligenceResult),
                    content, route, window, request, false);
        }
        publishValidation(validated, validationStartedAt);
        if (!validated.isValid()) {
            publishFallback(
                    FallbackTrigger.VALIDATION_REJECTED,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return finalizeTurn(
                    hybridFallback(deterministic, intelligenceResult),
                    content, route, window, request, false);
        }
        ConversationAnswerResult modelResult = new ConversationAnswerResult(
                request.getTurnId(),
                deterministic.getContentVersion(),
                ConversationIntent.HYBRID,
                ConversationAnswerScope.HYBRID,
                validated.getResolution(),
                validated.getTitle(),
                validated.getAcceptedBlocks(),
                List.of(),
                intelligenceResult.isDegraded(),
                GenerationMode.MODEL,
                AnswerSource.RETRIEVAL,
                intelligenceResult.getNoticeCode(),
                deterministic.getProgress(),
                intelligenceResult.getPortfolioRecommendation());
        return finalizeTurn(modelResult, content, route, window, request, false);
    }

    private ConversationAnswerResult hybridFallback(
            ConversationAnswerResult deterministic,
            PortfolioIntelligenceResult intelligenceResult) {
        String noticeCode = intelligenceResult.getNoticeCode() == null
                ? "MODEL_UNAVAILABLE_FALLBACK"
                : intelligenceResult.getNoticeCode();
        return new ConversationAnswerResult(
                deterministic.getTurnId(),
                deterministic.getContentVersion(),
                ConversationIntent.HYBRID,
                ConversationAnswerScope.PORTFOLIO,
                deterministic.getResolution(),
                deterministic.getTitle(),
                deterministic.getBlocks(),
                List.of(),
                true,
                GenerationMode.FALLBACK,
                AnswerSource.RETRIEVAL,
                noticeCode,
                deterministic.getProgress(),
                deterministic.getPortfolioRecommendation());
    }

    private PortfolioTask withSubjectConstraint(
            PortfolioTask task,
            RuntimeAnswerContent content,
            ConversationRoute route) {
        String subjectId = null;
        if (hasText(route.getProjectSlug())) {
            subjectId = content.getProjects().stream()
                    .filter(project -> route.getProjectSlug().equals(project.getSlug()))
                    .map(project -> project.getStableId())
                    .findFirst()
                    .orElse(null);
        } else if (hasText(route.getCaseSlug())) {
            subjectId = content.getCases().stream()
                    .filter(caseItem -> route.getCaseSlug().equals(caseItem.getSlug()))
                    .map(caseItem -> caseItem.getStableId())
                    .findFirst()
                    .orElse(null);
        }
        if (subjectId == null) {
            return task;
        }
        return new PortfolioTask(
                task.getTurnId(),
                task.getQuestion(),
                task.getMode(),
                task.getConfidence(),
                task.getConditions(),
                task.getRecommendationContext(),
                task.getRefinement(),
                subjectId);
    }

    private void publishPortfolioIntelligence(
            PortfolioIntelligenceResult result,
            boolean contextPresent,
            long startedAt) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        int recommendationCount = result.getPortfolioRecommendation() == null
                ? 0
                : result.getPortfolioRecommendation().getItems().size();
        String validationResult = result.getResolvedIntent()
                == com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode.CLARIFICATION_REQUIRED
                ? "CLARIFICATION_REQUIRED"
                : "ACCEPTED";
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "portfolio.intelligence.completed",
                            result.isDegraded() ? DiagnosticLevel.WARN : DiagnosticLevel.DEBUG)
                    .field("task.mode", result.getResolvedIntent())
                    .field("subject.count", result.getSubjects().size())
                    .field("evidence.count", result.getEvidence().size())
                    .field("recommendation.count", recommendationCount)
                    .field("context.present", contextPresent)
                    .field("validation.result", validationResult)
                    .field(
                            "duration.bucket",
                            DurationBuckets.fromElapsedMillis(elapsedMillis))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PortfolioRecommendationContext recommendationContext(ConversationAnswerRequest request) {
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
                || (route.getProjectSlug() == null
                && route.getCaseSlug() == null)) {
            progress = new ConversationProgress(
                    progress.getCoveredTopics(),
                    ConversationGuidanceStage.EXPLORE_OTHERS);
        }
        List<ConversationSuggestedQuestion> suggestions =
                questionService.generate(
                        content,
                        route,
                        window,
                        base.getBlocks(),
                        progress,
                        request.getQuestion(),
                        base.getGenerationMode() == GenerationMode.MODEL);
        return base.withGuidance(suggestions, progress);
    }

    private ConversationRoute safeRoute(
            ConversationAnswerRequest request,
            boolean clearSubject
    ) {
        String projectSlug = clearSubject
                ? null
                : request.getContext().getProjectSlug();
        String caseSlug = clearSubject
                ? null
                : request.getContext().getCaseSlug();
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0d,
                projectSlug,
                caseSlug,
                progressClassifier.inferFacet(request.getQuestion()),
                false);
    }

    private ConversationRoute portfolioBoundary(ConversationIntent intent) {
        ConversationAnswerScope scope = intent == ConversationIntent.TIME_SENSITIVE
                ? ConversationAnswerScope.GENERAL
                : ConversationAnswerScope.CONVERSATION;
        return new ConversationRoute(
                intent,
                scope,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private ConversationRoute withRequestSubject(
            ConversationRoute route,
            ConversationAnswerRequest request
    ) {
        if (route.getProjectSlug() != null || route.getCaseSlug() != null) {
            return route;
        }
        return new ConversationRoute(
                route.getIntent(),
                route.getAnswerScope(),
                route.getConfidence(),
                request.getContext().getProjectSlug(),
                request.getContext().getCaseSlug(),
                route.getFacet(),
                route.isClarificationRequired());
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
        String failureCode = validation.isValid()
                ? "NONE"
                : validation.getFailureCode();
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "answer.validation.completed",
                            validation.isValid()
                                    ? DiagnosticLevel.DEBUG
                                    : DiagnosticLevel.WARN)
                    .field("validation.accepted", validation.isValid())
                    .field("failure.code", failureCode)
                    .field(
                            "duration.bucket",
                            DurationBuckets.fromElapsedMillis(elapsedMillis))
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
                            "answer.fallback.selected",
                            DiagnosticLevel.WARN)
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
