package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDecision;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
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
        if (!subjectGuard.accepts(request.getContext(), content)) {
            return fallback.unknownSubject(request, content);
        }
        if (!providerAccess.isAllowed()) {
            return fallback.answer(request, content);
        }
        ConversationWindow window = windowManager.prepare(
                request.getMessages(), request.getQuestion());
        ConversationRoute route = intentRouter.route(content, window, request);
        if (route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.TIME_SENSITIVE
                || route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            return fallback.answer(request, content, route);
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
            return fallbackResult;
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
            return fallbackResult;
        }
        publishValidation(validated, validationStartedAt);
        if (!validated.isValid()) {
            ConversationAnswerResult fallbackResult =
                    fallback.answer(request, content, route);
            publishFallback(
                    FallbackTrigger.VALIDATION_REJECTED,
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED);
            return fallbackResult;
        }
        List<ConversationSuggestedQuestion> suggestions = questionService.generate(
                content, route, window, validated.getAcceptedBlocks());
        return new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                route.getIntent(),
                route.getAnswerScope(),
                validated.getResolution(),
                validated.getTitle(),
                validated.getAcceptedBlocks(),
                suggestions,
                false,
                GenerationMode.MODEL,
                route.getAnswerScope() == ConversationAnswerScope.PORTFOLIO
                        || route.getAnswerScope() == ConversationAnswerScope.HYBRID
                        ? AnswerSource.RETRIEVAL
                        : null,
                null);
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
