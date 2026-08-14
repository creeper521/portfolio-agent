package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDecision;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.mapper.SemanticTurnRequestMapper;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.service.PlanConfirmationService;
import com.portfolio.agent.answer.routing.service.SemanticTurnCoordinator;
import com.portfolio.agent.answer.routing.service.SemanticTurnContractPolicy;
import com.portfolio.agent.answer.routing.service.SemanticTurnDecision;
import com.portfolio.agent.answer.routing.service.TurnRouter;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import com.portfolio.agent.answer.context.service.AuthorizedContextReferenceService;
import com.portfolio.agent.answer.context.service.AuthorizedContextReferenceResult;
import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.domain.ContextInvalidation;
import com.portfolio.agent.answer.domain.ContextResolution;

/**
 * Single runtime authority for an Agent turn. Legacy single-task capabilities
 * may execute only after a trusted semantic plan selects them.
 */
public final class ConversationalAgentRuntime {

    private static final String SEMANTIC_CAPABILITY_SET_VERSION = "semantic-routing-v1";

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final SemanticTurnRequestMapper requestMapper;
    private final TurnRouter turnRouter;
    private final PlanConfirmationService confirmationService;
    private final SemanticTurnCoordinator coordinator;
    private final ConversationDecisionPublisher decisionPublisher;
    private final DiagnosticEventPublisher diagnosticEventPublisher;
    private final AuthorizedContextReferenceService contextReferenceService;
    private final SemanticTurnContractPolicy contractPolicy = new SemanticTurnContractPolicy();

    public ConversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            SemanticTurnRequestMapper requestMapper,
            TurnRouter turnRouter,
            PlanConfirmationService confirmationService,
            SemanticTurnCoordinator coordinator,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher) {
        this(knowledgeGateway, requestMapper, turnRouter, confirmationService, coordinator,
                decisionPublisher, diagnosticEventPublisher, null);
    }

    public ConversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            SemanticTurnRequestMapper requestMapper,
            TurnRouter turnRouter,
            PlanConfirmationService confirmationService,
            SemanticTurnCoordinator coordinator,
            ConversationDecisionPublisher decisionPublisher,
            DiagnosticEventPublisher diagnosticEventPublisher,
            AuthorizedContextReferenceService contextReferenceService) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper");
        this.turnRouter = Objects.requireNonNull(turnRouter, "turnRouter");
        this.confirmationService = Objects.requireNonNull(confirmationService, "confirmationService");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.decisionPublisher = Objects.requireNonNull(decisionPublisher, "decisionPublisher");
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher, "diagnosticEventPublisher");
        this.contextReferenceService = contextReferenceService;
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request) {
        return answer(request, null);
    }

    public ConversationAnswerResult answer(
            ConversationAnswerRequest request, ConversationRequestContext requestContext) {
        Objects.requireNonNull(request, "request");
        long startedAt = System.nanoTime();
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        SemanticTurnInput input = requestMapper.toInput(request, content.getContentVersion());
        ContextAuthorization authorization = authorizeContextReferences(
                request, requestContext, content.getContentVersion());
        List<AuthorizedContextReference> authorizedContextReferences = authorization.references();
        for (AuthorizedContextReference reference : authorizedContextReferences) {
            if (reference.getSelectedSubject().isPresent()) {
                input = input.withExplicitSubjectReference(reference.getSelectedSubject().orElseThrow());
            }
        }
        AgentTurnResult contextFailure = authorization.failure();
        AgentTurnResult presetFailure = input.getAction() == SemanticTurnInput.Action.CONFIRM_PLAN
                ? null : validatePreset(request, content);
        if (contextFailure == null && presetFailure == null
                && input.getAction() != SemanticTurnInput.Action.CONFIRM_PLAN) {
            input = bindPublishedPresetSubject(input, request, content);
        }
        AgentTurnResult agentTurn = contextFailure != null ? contextFailure
                : presetFailure != null ? presetFailure
                : input.getAction() == SemanticTurnInput.Action.CONFIRM_PLAN
                ? confirmationService.executeVerified(
                        input.getConfirmationSubmission(),
                        versionBinding(content, contractPolicy.resolve(request.getAgentTurnContract())),
                        coordinator, isStpV1(input))
                : routeAndHandle(input, content, authorizedContextReferences);
        if (contextFailure == null && authorization.resolution() != null) {
            agentTurn = agentTurn.withContextResolution(authorization.resolution());
        }
        ConversationAnswerResult result = result(request, content, agentTurn)
                .withContractIdentity(request.getQuestionPresetId(), request.getContractVersion());
        publishSemanticDiagnostics(agentTurn);
        publishBestEffort(result, startedAt);
        return result;
    }

    private AgentTurnResult routeAndHandle(
            SemanticTurnInput input,
            RuntimeAnswerContent content,
            List<AuthorizedContextReference> authorizedContextReferences) {
        SemanticTurnDecision decision = turnRouter.route(input);
        if (decision.getDisposition() == SemanticTurnDecision.Disposition.CONFIRMATION_REQUIRED) {
            try {
                return AgentTurnResult.fromDecision(
                        decision,
                        confirmationService.issue(
                                decision.getValidatedPlan().orElseThrow(),
                                versionBinding(content, contractPolicy.resolve(input.getAgentTurnContract()))),
                        null,
                        isStpV1(input));
            } catch (RuntimeException exception) {
                return AgentTurnResult.rejected(Set.of("PLAN_CONFIRMATION_UNAVAILABLE"));
            }
        }
        if (decision.getDisposition() == SemanticTurnDecision.Disposition.READY
                || decision.getDisposition() == SemanticTurnDecision.Disposition.PARTIAL_READY) {
            SemanticTurnOutcome outcome = coordinator.execute(
                    decision.getValidatedPlan().orElseThrow(),
                    decision.getExecutionSelection().orElseThrow(), authorizedContextReferences,
                    input.getQuestionPresetId() != null);
            return AgentTurnResult.fromDecision(
                    decision, null, outcome, isStpV1(input));
        }
        return AgentTurnResult.fromDecision(
                decision, null, null, isStpV1(input));
    }

    private boolean isStpV1(SemanticTurnInput input) {
        return SemanticTurnContractPolicy.COMPATIBILITY_CONTRACT.equals(
                contractPolicy.resolve(input.getAgentTurnContract()));
    }

    private ContextAuthorization authorizeContextReferences(
            ConversationAnswerRequest request,
            ConversationRequestContext requestContext,
            String currentContentVersion) {
        if (request.getContextReference() == null || requestContext == null
                || contextReferenceService == null) {
            return new ContextAuthorization(requestContext == null || contextReferenceService == null
                    ? List.of()
                    : contextReferenceService.authorizeActive(
                            requestContext.getConversationId(), requestContext.getResumeToken(), Instant.now()), null);
        }
        AuthorizedContextReference requested = new AuthorizedContextReference(
                request.getContextReference().getContextHandle(),
                request.getContextReference().getExpectedContextType().name(),
                null,
                request.getContextReference().getResultItemId());
        AuthorizedContextReferenceResult authorization = contextReferenceService.authorizeDetailed(
                requestContext.getConversationId(), requestContext.getResumeToken(),
                requested, Instant.now(), currentContentVersion);
        if (authorization.getReference().isPresent()) {
            ContextResolution resolution = authorization.getVersionDecision()
                    .filter(value -> value.getStatus()
                            == com.portfolio.agent.answer.context.service.ContextVersionStatus.REVALIDATED)
                    .map(value -> new ContextResolution("REVALIDATED_TO_CURRENT",
                            request.getContextReference().getExpectedContextType(), currentContentVersion))
                    .orElse(null);
            return new ContextAuthorization(
                    List.of(authorization.getReference().orElseThrow()), null, resolution);
        }
        String contract = contractPolicy.resolve(request.getAgentTurnContract());
        if (SemanticTurnContractPolicy.COMPATIBILITY_CONTRACT.equals(contract)) {
            return new ContextAuthorization(List.of(),
                    AgentTurnResult.rejected(Set.of("CONTEXT_DEPENDENCY_UNAVAILABLE")), null);
        }
        ConversationContextResolution.Status status = authorization.getResolution().getStatus();
        if (status == ConversationContextResolution.Status.UNAVAILABLE) {
            return new ContextAuthorization(List.of(), AgentTurnResult.rejected(
                    Set.of("CONTEXT_RESOLUTION_UNAVAILABLE"), false), null);
        }
        ConversationContextType type = request.getContextReference().getExpectedContextType();
        String reason = contextReason(status, requested.getResultItemId().isPresent());
        ContextInvalidation invalidation = new ContextInvalidation(
                reason, recoveryAction(reason), type, currentContentVersion);
        return new ContextAuthorization(List.of(), AgentTurnResult.contextInvalidated(
                invalidation, null, false), null);
    }

    private String contextReason(
            ConversationContextResolution.Status status, boolean hasResultItemReference) {
        return switch (status) {
            case INVALID_REFERENCE -> "CONTEXT_REFERENCE_INVALID";
            case EXPIRED -> "CONTEXT_REFERENCE_EXPIRED";
            case INCOMPATIBLE -> hasResultItemReference
                    ? "REFERENCED_SUBJECT_UNAVAILABLE" : "CONTEXT_RESULT_STALE";
            case CLARIFICATION_REQUIRED -> "RESULT_CONTEXT_AMBIGUITY";
            case UNAVAILABLE, RESOLVED -> throw new IllegalStateException(
                    "non-invalidation Context status cannot be projected");
        };
    }

    private String recoveryAction(String reason) {
        return switch (reason) {
            case "CONTEXT_REFERENCE_INVALID", "CONTEXT_REFERENCE_EXPIRED" -> "REASK_WITHOUT_CONTEXT";
            case "REFERENCED_SUBJECT_UNAVAILABLE", "RESULT_CONTEXT_AMBIGUITY" -> "RESELECT_RESULTS";
            case "CONTEXT_RESULT_STALE", "REFERENCED_PUBLIC_SOURCE_CHANGED" ->
                    "RESTART_FROM_CURRENT_CONTENT";
            default -> throw new IllegalArgumentException("unsupported Context invalidation reason");
        };
    }

    private static final class ContextAuthorization {
        private final List<AuthorizedContextReference> references;
        private final AgentTurnResult failure;
        private final ContextResolution resolution;

        private ContextAuthorization(List<AuthorizedContextReference> references, AgentTurnResult failure) {
            this(references, failure, null);
        }

        private ContextAuthorization(
                List<AuthorizedContextReference> references,
                AgentTurnResult failure,
                ContextResolution resolution) {
            this.references = List.copyOf(references);
            this.failure = failure;
            this.resolution = resolution;
        }

        private List<AuthorizedContextReference> references() { return references; }
        private AgentTurnResult failure() { return failure; }
        private ContextResolution resolution() { return resolution; }
    }

    private PlanConfirmation.VersionBinding versionBinding(
            RuntimeAnswerContent content, String semanticSchemaVersion) {
        return new PlanConfirmation.VersionBinding(
                semanticSchemaVersion,
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                SEMANTIC_CAPABILITY_SET_VERSION);
    }

    private ConversationAnswerResult result(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            AgentTurnResult agentTurn) {
        ConversationAnswerScope answerScope = projectedScope(request, agentTurn);
        List<ConversationAnswerBlock> blocks = projectedBlocks(request, content, agentTurn);
        return new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                projectedIntent(request, agentTurn, answerScope),
                answerScope,
                projectedResolution(request, content, agentTurn),
                title(agentTurn),
                blocks,
                List.of(),
                isDegraded(agentTurn),
                projectedGenerationMode(agentTurn),
                null,
                projectedNoticeCode(agentTurn),
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING),
                null,
                projectedConstructionMode(request, content, agentTurn),
                projectedIntentSource(request, agentTurn),
                projectedEvidenceState(request, content, answerScope, agentTurn)).withAgentTurn(agentTurn);
    }

    private AgentTurnResult validatePreset(
            ConversationAnswerRequest request, RuntimeAnswerContent content) {
        String presetId = request.getQuestionPresetId();
        if (presetId == null || presetId.isBlank()) {
            return null;
        }
        AnswerQuestion matched = null;
        for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : content.getProjects()) {
            matched = findQuestion(subject, presetId);
            if (matched != null) {
                break;
            }
        }
        if (matched == null) {
            for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : content.getCases()) {
                matched = findQuestion(subject, presetId);
                if (matched != null) {
                    break;
                }
            }
        }
        if (matched == null || !matched.isActiveContract()) {
            return AgentTurnResult.rejected(Set.of("PRESET_CONTRACT_UNAVAILABLE"));
        }
        if (request.getContractVersion() == null
                || !request.getContractVersion().equals(matched.getContractVersion())) {
            return AgentTurnResult.rejected(Set.of("PRESET_CONTRACT_STALE"));
        }
        return null;
    }

    private SemanticTurnInput bindPublishedPresetSubject(
            SemanticTurnInput input,
            ConversationAnswerRequest request,
            RuntimeAnswerContent content) {
        String presetId = request.getQuestionPresetId();
        if (presetId == null || presetId.isBlank()) {
            return input;
        }
        for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : content.getProjects()) {
            if (findQuestion(subject, presetId) != null) {
                return input.withExplicitSubjectReference(new com.portfolio.agent.answer.routing.domain.SubjectReference(
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.PROJECT,
                        subject.getStableId(),
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource.EXPLICIT_REFERENCE,
                        content.getContentVersion()));
            }
        }
        for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : content.getCases()) {
            if (findQuestion(subject, presetId) != null) {
                return input.withExplicitSubjectReference(new com.portfolio.agent.answer.routing.domain.SubjectReference(
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.CASE,
                        subject.getStableId(),
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource.EXPLICIT_REFERENCE,
                        content.getContentVersion()));
            }
        }
        return input;
    }

    private AnswerQuestion findQuestion(
            com.portfolio.agent.answer.domain.AnswerKnowledge subject, String presetId) {
        for (AnswerQuestion question : subject.getQuestions()) {
            if (presetId.equals(question.getId())) {
                return question;
            }
        }
        return null;
    }

    private ConversationAnswerScope projectedScope(
            ConversationAnswerRequest request, AgentTurnResult agentTurn) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return ConversationAnswerScope.PORTFOLIO;
        }
        if ((hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")
                || hasReason(agentTurn, "PRESET_CONTRACT_STALE"))
                && hasStructuredSubject(request)) {
            return ConversationAnswerScope.PORTFOLIO;
        }
        return scope(agentTurn);
    }

    private ConversationIntent projectedIntent(
            ConversationAnswerRequest request,
            AgentTurnResult agentTurn,
            ConversationAnswerScope answerScope) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return ConversationIntent.PORTFOLIO_GROUNDED;
        }
        if (hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")
                || hasReason(agentTurn, "PRESET_CONTRACT_STALE")) {
            return hasStructuredSubject(request)
                    ? ConversationIntent.PORTFOLIO_GROUNDED : ConversationIntent.GENERAL_KNOWLEDGE;
        }
        if (agentTurn.getDisposition() == AgentTurnResult.Disposition.BOUNDARY) {
            return ConversationIntent.UNSUPPORTED_OR_UNSAFE;
        }
        return switch (answerScope) {
            case PORTFOLIO -> ConversationIntent.PORTFOLIO_GROUNDED;
            case MIXED, HYBRID -> ConversationIntent.HYBRID;
            case GENERAL, CONVERSATION, GLOBAL -> ConversationIntent.GENERAL_KNOWLEDGE;
        };
    }

    private AnswerResolution projectedResolution(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            AgentTurnResult agentTurn) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return AnswerResolution.INVALID_INPUT;
        }
        if (hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")
                || hasReason(agentTurn, "PRESET_CONTRACT_STALE")) {
            return AnswerResolution.CAPABILITY_UNAVAILABLE;
        }
        List<TaskOutcome> primaryOutcomes = primaryOutcomes(agentTurn);
        if (!primaryOutcomes.isEmpty()) {
            boolean primaryHasRenderablePayload = primaryOutcomes.stream()
                    .anyMatch(TaskOutcome::hasRenderablePayload);
            boolean primaryFailed = primaryOutcomes.stream().anyMatch(outcome ->
                    outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.FAILED);
            if (primaryFailed && !primaryHasRenderablePayload) {
                return AnswerResolution.CAPABILITY_UNAVAILABLE;
            }
            if (primaryHasRenderablePayload) {
                boolean partial = primaryOutcomes.stream().anyMatch(outcome ->
                        outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED
                                || !outcome.hasRenderablePayload());
                return partial ? AnswerResolution.PARTIALLY_ANSWERED : AnswerResolution.ANSWERED;
            }
            if (hasProjectedFallback(request, content, agentTurn)) {
                return AnswerResolution.ANSWERED;
            }
            return resolution(agentTurn);
        }
        if (hasFailedExecution(agentTurn)) {
            return AnswerResolution.CAPABILITY_UNAVAILABLE;
        }
        if (hasProjectedFallback(request, content, agentTurn)) {
            return AnswerResolution.ANSWERED;
        }
        if (hasRenderablePayload(agentTurn)) {
            return AnswerResolution.PARTIALLY_ANSWERED;
        }
        return resolution(agentTurn);
    }

    private List<TaskOutcome> primaryOutcomes(AgentTurnResult agentTurn) {
        if (agentTurn.getPlan().isEmpty() || agentTurn.getOutcome().isEmpty()) {
            return List.of();
        }
        Set<String> primaryTaskIds = agentTurn.getPlan().orElseThrow().getTasks().stream()
                .filter(task -> task.getFulfillmentRole()
                        == com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole.PRIMARY)
                .map(com.portfolio.agent.answer.routing.domain.SemanticTask::getTaskId)
                .collect(java.util.stream.Collectors.toSet());
        return agentTurn.getOutcome().orElseThrow().getTaskOutcomes().stream()
                .filter(outcome -> primaryTaskIds.contains(outcome.getTaskId()))
                .toList();
    }

    private List<ConversationAnswerBlock> projectedBlocks(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            AgentTurnResult agentTurn) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return List.of(new ConversationAnswerBlock(
                    com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO,
                    "无法验证公开主体", List.of(), List.of()));
        }
        // Preset 的可见答案必须由已审核合同决定。只绑定主体后再依赖自由文本路由，
        // 会把“为什么/如何”这类问题退化成无关的项目背景或交付状态。
        if (hasValidPreset(request, content)) {
            return presetBlocks(request, content);
        }
        return List.of();
    }

    private String projectedNoticeCode(AgentTurnResult agentTurn) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return "STRUCTURED_SUBJECT_INVALID";
        }
        if (hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")) {
            return "PRESET_CONTRACT_UNAVAILABLE";
        }
        if (hasReason(agentTurn, "PRESET_CONTRACT_STALE")) {
            return "PRESET_CONTRACT_STALE";
        }
        return null;
    }

    private AnswerConstructionMode projectedConstructionMode(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            AgentTurnResult agentTurn) {
        if (hasRenderableGeneralPayload(agentTurn)) {
            return hasRenderableNonGeneralPayload(agentTurn)
                    ? AnswerConstructionMode.MIXED_COMPOSITION
                    : AnswerConstructionMode.GENERAL_MODEL;
        }
        AnswerConstructionMode compositionMode = projectedCompositionMode(agentTurn);
        if (compositionMode != null) {
            return compositionMode;
        }
        return hasRenderablePayload(agentTurn)
                || hasProjectedFallback(request, content, agentTurn)
                ? AnswerConstructionMode.EVIDENCE_COMPOSITION : AnswerConstructionMode.TEMPLATE;
    }

    private GenerationMode projectedGenerationMode(AgentTurnResult agentTurn) {
        if (hasRenderableGeneralPayload(agentTurn)) {
            return hasRenderableNonGeneralPayload(agentTurn)
                    ? GenerationMode.MIXED : GenerationMode.MODEL;
        }
        return projectedGenerationModeForComposition(agentTurn);
    }

    private boolean hasRenderableNonGeneralPayload(AgentTurnResult agentTurn) {
        return agentTurn.getOutcome().map(outcome -> outcome.getTaskOutcomes().stream()
                .anyMatch(task -> task.hasRenderablePayload()
                        && task.getSourceDomain() != TaskSourceDomain.GENERAL)).orElse(false);
    }

    private GenerationMode projectedGenerationModeForComposition(AgentTurnResult agentTurn) {
        java.util.Set<com.portfolio.agent.answer.composition.domain.CompositionMode> modes =
                compositionModes(agentTurn);
        if (modes.contains(com.portfolio.agent.answer.composition.domain.CompositionMode.MODEL_GROUNDED)) {
            return modes.size() == 1 ? GenerationMode.MODEL : GenerationMode.MIXED;
        }
        if (modes.contains(com.portfolio.agent.answer.composition.domain.CompositionMode.FALLBACK)) {
            return modes.size() == 1 ? GenerationMode.FALLBACK : GenerationMode.MIXED;
        }
        return GenerationMode.DETERMINISTIC;
    }

    private AnswerConstructionMode projectedCompositionMode(AgentTurnResult agentTurn) {
        java.util.Set<com.portfolio.agent.answer.composition.domain.CompositionMode> modes =
                compositionModes(agentTurn);
        if (modes.contains(com.portfolio.agent.answer.composition.domain.CompositionMode.MODEL_GROUNDED)) {
            return modes.size() == 1
                    ? AnswerConstructionMode.MODEL_GROUNDED
                    : AnswerConstructionMode.MIXED_COMPOSITION;
        }
        if (modes.contains(com.portfolio.agent.answer.composition.domain.CompositionMode.FALLBACK)) {
            return modes.size() == 1 ? AnswerConstructionMode.EVIDENCE_COMPOSITION
                    : AnswerConstructionMode.MIXED_COMPOSITION;
        }
        return null;
    }

    private java.util.Set<com.portfolio.agent.answer.composition.domain.CompositionMode> compositionModes(
            AgentTurnResult agentTurn) {
        java.util.Set<com.portfolio.agent.answer.composition.domain.CompositionMode> modes =
                new java.util.LinkedHashSet<>();
        agentTurn.getOutcome().ifPresent(outcome -> outcome.getTaskOutcomes().stream()
                .filter(com.portfolio.agent.answer.routing.domain.TaskOutcome::hasRenderablePayload)
                .flatMap(value -> value.getComposition().stream())
                .map(com.portfolio.agent.answer.routing.domain.TaskComposition::getMode)
                .forEach(modes::add));
        return java.util.Set.copyOf(modes);
    }

    private AnswerIntentSource projectedIntentSource(
            ConversationAnswerRequest request, AgentTurnResult agentTurn) {
        if (request.getQuestionPresetId() != null) {
            return AnswerIntentSource.PRESET;
        }
        return agentTurn.getPlan()
                .filter(plan -> plan.getSource()
                        == com.portfolio.agent.answer.routing.domain.SemanticTurnPlan.PlanSource.MODEL_ASSISTED)
                .map(ignored -> AnswerIntentSource.MODEL)
                .orElse(AnswerIntentSource.RULE);
    }

    private AnswerEvidenceState projectedEvidenceState(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationAnswerScope answerScope,
            AgentTurnResult agentTurn) {
        if (hasRenderableGeneralPayload(agentTurn)
                && answerScope == ConversationAnswerScope.GENERAL) {
            return AnswerEvidenceState.NOT_REQUIRED;
        }
        if (hasRenderableGeneralPayload(agentTurn) && hasRenderableNonGeneralPayload(agentTurn)) {
            return AnswerEvidenceState.MIXED;
        }
        if (hasRenderablePayload(agentTurn)
                || hasProjectedFallback(request, content, agentTurn)) {
            return AnswerEvidenceState.VERIFIED;
        }
        if (answerScope == ConversationAnswerScope.PORTFOLIO) {
            return AnswerEvidenceState.INSUFFICIENT;
        }
        return AnswerEvidenceState.NOT_REQUIRED;
    }

    private boolean hasValidPreset(ConversationAnswerRequest request, RuntimeAnswerContent content) {
        return request.getQuestionPresetId() != null
                && validatePreset(request, content) == null;
    }

    private List<ConversationAnswerBlock> presetBlocks(
            ConversationAnswerRequest request, RuntimeAnswerContent content) {
        String presetId = request.getQuestionPresetId();
        if (presetId == null || validatePreset(request, content) != null) {
            return List.of();
        }
        for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : allSubjects(content)) {
            for (AnswerQuestion question : subject.getQuestions()) {
                if (!presetId.equals(question.getId())) {
                    continue;
                }
                java.util.Set<String> claimIds = new java.util.LinkedHashSet<>();
                claimIds.addAll(question.getRequiredClaimIds());
                claimIds.addAll(question.getSupportingClaimIds());
                List<ConversationAnswerBlock> blocks = new java.util.ArrayList<>();
                for (com.portfolio.agent.answer.domain.AnswerClaimProjection claim : subject.getClaims()) {
                    if (!claimIds.isEmpty() && !claimIds.contains(claim.getId())) {
                        continue;
                    }
                    if (claimIds.isEmpty()
                            && !question.getPreferredClaimCategories().isEmpty()
                            && !question.getPreferredClaimCategories().contains(claim.getCategory())) {
                        continue;
                    }
                    blocks.add(new ConversationAnswerBlock(
                            com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO,
                            presetSectionType(claim), presetSectionTitle(claim),
                            presetClaimContent(claim),
                            List.of(claim.getId()), claim.getDirectEvidenceIds()));
                }
                if (!blocks.isEmpty()) {
                    return List.copyOf(blocks);
                }
            }
        }
        return List.of();
    }

    private static String presetClaimContent(
            com.portfolio.agent.answer.domain.AnswerClaimProjection claim) {
        String statement = claim.getStatement();
        String detail = claim.getDetail();
        if (detail == null || detail.isBlank()) {
            return statement;
        }
        return statement + " " + detail;
    }

    private static com.portfolio.agent.answer.domain.AnswerSectionType presetSectionType(
            com.portfolio.agent.answer.domain.AnswerClaimProjection claim) {
        return switch (claim.getCategory()) {
            case BACKGROUND -> com.portfolio.agent.answer.domain.AnswerSectionType.BACKGROUND;
            case RESPONSIBILITY -> com.portfolio.agent.answer.domain.AnswerSectionType.RESPONSIBILITY;
            case TECHNICAL_DECISION, IMPLEMENTATION ->
                    com.portfolio.agent.answer.domain.AnswerSectionType.SOLUTION;
            case VERIFICATION -> com.portfolio.agent.answer.domain.AnswerSectionType.VERIFICATION;
            case OUTCOME -> com.portfolio.agent.answer.domain.AnswerSectionType.STATUS;
            case LIMITATION, LEARNING, REFLECTION ->
                    com.portfolio.agent.answer.domain.AnswerSectionType.BOUNDARY;
        };
    }

    private static String presetSectionTitle(
            com.portfolio.agent.answer.domain.AnswerClaimProjection claim) {
        return switch (presetSectionType(claim)) {
            case BACKGROUND -> "背景";
            case RESPONSIBILITY -> "我的职责";
            case SOLUTION -> "处理方案";
            case VERIFICATION -> "验证过程";
            case STATUS -> "结果与状态";
            case BOUNDARY -> "边界与复盘";
            case REJECTED -> "说明";
        };
    }

    private List<ConversationAnswerBlock> structuredSubjectBlocks(
            ConversationAnswerRequest request, RuntimeAnswerContent content) {
        if (request.getContext() == null) {
            return List.of();
        }
        String subjectHint = request.getContext().getProjectSlug() != null
                ? request.getContext().getProjectSlug() : request.getContext().getCaseSlug();
        if (subjectHint == null || subjectHint.isBlank()) {
            return List.of();
        }
        for (com.portfolio.agent.answer.domain.AnswerKnowledge subject : allSubjects(content)) {
            if (!subjectHint.equals(subject.getStableId()) && !subjectHint.equals(subject.getSlug())) {
                continue;
            }
            List<ConversationAnswerBlock> blocks = new java.util.ArrayList<>();
            for (com.portfolio.agent.answer.domain.AnswerClaimProjection claim : subject.getClaims()) {
                if (claim.getStatement() == null || claim.getStatement().isBlank()) {
                    continue;
                }
                blocks.add(new ConversationAnswerBlock(
                        com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO,
                        claim.getStatement(), List.of(claim.getId()), claim.getDirectEvidenceIds()));
            }
            return List.copyOf(blocks);
        }
        return List.of();
    }

    private boolean hasProjectedFallback(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            AgentTurnResult agentTurn) {
        if (!isReadyPortfolioTurn(agentTurn)
                || hasRenderablePayload(agentTurn)
                || hasFailedExecution(agentTurn)) {
            return false;
        }
        if (hasValidPreset(request, content) && !presetBlocks(request, content).isEmpty()) {
            return true;
        }
        return false;
    }

    private boolean isReadyPortfolioTurn(AgentTurnResult agentTurn) {
        if (agentTurn.getDisposition() != AgentTurnResult.Disposition.READY
                && agentTurn.getDisposition() != AgentTurnResult.Disposition.PARTIAL_READY) {
            return false;
        }
        return scope(agentTurn) == ConversationAnswerScope.PORTFOLIO;
    }

    private List<com.portfolio.agent.answer.domain.AnswerKnowledge> allSubjects(RuntimeAnswerContent content) {
        List<com.portfolio.agent.answer.domain.AnswerKnowledge> subjects = new java.util.ArrayList<>();
        subjects.addAll(content.getProjects());
        subjects.addAll(content.getCases());
        return List.copyOf(subjects);
    }

    private boolean hasRenderablePayload(AgentTurnResult agentTurn) {
        return agentTurn.getOutcome().map(value -> value.getTaskOutcomes().stream()
                .anyMatch(TaskOutcome::hasRenderablePayload)).orElse(false);
    }

    private boolean hasFailedExecution(AgentTurnResult agentTurn) {
        return agentTurn.getOutcome().map(outcome -> outcome.getTaskOutcomes().stream()
                .anyMatch(value -> value.getExecutionStatus()
                        == TaskOutcome.TaskExecutionStatus.FAILED)).orElse(false);
    }

    private boolean hasRenderableGeneralPayload(AgentTurnResult agentTurn) {
        return agentTurn.getOutcome().map(value -> value.getTaskOutcomes().stream()
                .anyMatch(outcome -> outcome.getSourceDomain() == TaskSourceDomain.GENERAL
                        && outcome.hasRenderablePayload())).orElse(false);
    }

    private boolean hasReason(AgentTurnResult agentTurn, String reason) {
        return agentTurn.getReasonCodes().contains(reason);
    }

    private boolean hasStructuredSubject(ConversationAnswerRequest request) {
        return request.getContext() != null
                && (request.getContext().getProjectSlug() != null
                || request.getContext().getCaseSlug() != null);
    }

    private ConversationIntent intent(AgentTurnResult agentTurn) {
        if (agentTurn.getDisposition() == AgentTurnResult.Disposition.BOUNDARY) {
            return ConversationIntent.UNSUPPORTED_OR_UNSAFE;
        }
        return switch (scope(agentTurn)) {
            case PORTFOLIO -> ConversationIntent.PORTFOLIO_GROUNDED;
            case MIXED, HYBRID -> ConversationIntent.HYBRID;
            case GENERAL, CONVERSATION, GLOBAL -> ConversationIntent.GENERAL_KNOWLEDGE;
        };
    }

    private ConversationAnswerScope scope(AgentTurnResult agentTurn) {
        Set<TaskSourceDomain> domains = new LinkedHashSet<>();
        agentTurn.getPlan().ifPresent(plan -> plan.getTasks().forEach(task -> domains.add(task.getSourceDomain())));
        if (domains.isEmpty()) {
            return ConversationAnswerScope.GLOBAL;
        }
        if (domains.size() == 1 && domains.contains(TaskSourceDomain.PORTFOLIO)) {
            return ConversationAnswerScope.PORTFOLIO;
        }
        if (domains.size() == 1 && domains.contains(TaskSourceDomain.GENERAL)) {
            return ConversationAnswerScope.GENERAL;
        }
        return ConversationAnswerScope.MIXED;
    }

    private AnswerResolution resolution(AgentTurnResult agentTurn) {
        return switch (agentTurn.getDisposition()) {
            case READY, PARTIAL_READY -> agentTurn.getOutcome()
                    .filter(outcome -> outcome.getAnsweredCount() > 0)
                    .map(outcome -> AnswerResolution.ANSWERED)
                    .orElse(AnswerResolution.NOT_SUPPORTED);
            case CONFIRMATION_REQUIRED -> AnswerResolution.AWAITING_CONFIRMATION;
            case CLARIFICATION_REQUIRED -> AnswerResolution.NEEDS_CLARIFICATION;
            case BOUNDARY, REJECTED -> AnswerResolution.REJECTED;
            case PLAN_INVALIDATED, CONTEXT_INVALIDATED -> AnswerResolution.NEEDS_CLARIFICATION;
        };
    }

    private String title(AgentTurnResult agentTurn) {
        return switch (agentTurn.getDisposition()) {
            case READY, PARTIAL_READY -> "处理结果";
            case CONFIRMATION_REQUIRED -> "请确认执行计划";
            case CLARIFICATION_REQUIRED -> "需要补充信息";
            case PLAN_INVALIDATED -> "计划需要重新生成";
            case BOUNDARY, REJECTED -> "无法处理此请求";
            default -> "context invalidated";
        };
    }

    private boolean isDegraded(AgentTurnResult agentTurn) {
        return agentTurn.getOutcome().map(outcome -> outcome.getDegradedCount() > 0).orElse(false);
    }

    private void publishSemanticDiagnostics(AgentTurnResult agentTurn) {
        int taskCount = agentTurn.getPlan().map(plan -> plan.getTasks().size()).orElse(0);
        int succeeded = 0;
        int blocked = 0;
        int failed = 0;
        String outcome = "NONE";
        if (agentTurn.getOutcome().isPresent()) {
            SemanticTurnOutcome turnOutcome = agentTurn.getOutcome().orElseThrow();
            outcome = turnOutcome.getPlanOutcome().name();
            for (TaskOutcome taskOutcome : turnOutcome.getTaskOutcomes()) {
                if (taskOutcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.SUCCEEDED) {
                    succeeded++;
                } else if (taskOutcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.BLOCKED) {
                    blocked++;
                } else if (taskOutcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.FAILED) {
                    failed++;
                }
            }
        }
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "semantic.turn.completed", DiagnosticLevel.INFO)
                    .field("plan.task.count", taskCount)
                    .field("plan.task.succeeded.count", succeeded)
                    .field("plan.task.blocked.count", blocked)
                    .field("plan.task.failed.count", failed)
                    .field("plan.outcome", outcome)
                    .field("plan.disposition", agentTurn.getDisposition())
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect a visitor response.
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
}
