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

/**
 * Single runtime authority for an Agent turn. Legacy single-task capabilities
 * may execute only after a trusted semantic plan selects them.
 */
public final class ConversationalAgentRuntime {

    private static final String SEMANTIC_SCHEMA_VERSION = "stp-v1";
    private static final String SEMANTIC_CAPABILITY_SET_VERSION = "semantic-routing-v1";

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final SemanticTurnRequestMapper requestMapper;
    private final TurnRouter turnRouter;
    private final PlanConfirmationService confirmationService;
    private final SemanticTurnCoordinator coordinator;
    private final ConversationDecisionPublisher decisionPublisher;
    private final DiagnosticEventPublisher diagnosticEventPublisher;
    private final AuthorizedContextReferenceService contextReferenceService;

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
        List<AuthorizedContextReference> authorizedContextReferences =
                authorizedContextReferences(request, requestContext);
        AgentTurnResult contextFailure = request.getContextReference() != null
                && authorizedContextReferences.isEmpty()
                ? AgentTurnResult.rejected(Set.of("CONTEXT_DEPENDENCY_UNAVAILABLE")) : null;
        AgentTurnResult presetFailure = input.getAction() == SemanticTurnInput.Action.CONFIRM_PLAN
                ? null : validatePreset(request, content);
        AgentTurnResult agentTurn = contextFailure != null ? contextFailure
                : presetFailure != null ? presetFailure
                : input.getAction() == SemanticTurnInput.Action.CONFIRM_PLAN
                ? confirmationService.executeVerified(
                        input.getConfirmationSubmission(), versionBinding(content), coordinator)
                : routeAndHandle(input, content, authorizedContextReferences);
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
                                decision.getValidatedPlan().orElseThrow(), versionBinding(content)),
                        null,
                        input.getAgentTurnContract() != null);
            } catch (RuntimeException exception) {
                return AgentTurnResult.rejected(Set.of("PLAN_CONFIRMATION_UNAVAILABLE"));
            }
        }
        if (decision.getDisposition() == SemanticTurnDecision.Disposition.READY
                || decision.getDisposition() == SemanticTurnDecision.Disposition.PARTIAL_READY) {
            SemanticTurnOutcome outcome = coordinator.execute(
                    decision.getValidatedPlan().orElseThrow(),
                    decision.getExecutionSelection().orElseThrow(), authorizedContextReferences);
            return AgentTurnResult.fromDecision(
                    decision, null, outcome, input.getAgentTurnContract() != null);
        }
        return AgentTurnResult.fromDecision(
                decision, null, null, input.getAgentTurnContract() != null);
    }

    private List<AuthorizedContextReference> authorizedContextReferences(
            ConversationAnswerRequest request,
            ConversationRequestContext requestContext) {
        if (request.getContextReference() == null || requestContext == null
                || contextReferenceService == null) {
            return requestContext == null || contextReferenceService == null
                    ? List.of()
                    : contextReferenceService.authorizeActive(
                            requestContext.getConversationId(), requestContext.getResumeToken(), Instant.now());
        }
        AuthorizedContextReference requested = new AuthorizedContextReference(
                request.getContextReference().getContextHandle(),
                request.getContextReference().getExpectedContextType().name());
        Optional<AuthorizedContextReference> authorized = contextReferenceService.authorize(
                requestContext.getConversationId(), requestContext.getResumeToken(),
                requested, Instant.now());
        return authorized.map(List::of).orElseGet(List::of);
    }

    private PlanConfirmation.VersionBinding versionBinding(RuntimeAnswerContent content) {
        return new PlanConfirmation.VersionBinding(
                SEMANTIC_SCHEMA_VERSION,
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
                projectedResolution(agentTurn),
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

    private AnswerResolution projectedResolution(AgentTurnResult agentTurn) {
        if (hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")) {
            return AnswerResolution.INVALID_INPUT;
        }
        if (hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")
                || hasReason(agentTurn, "PRESET_CONTRACT_STALE")) {
            return AnswerResolution.CAPABILITY_UNAVAILABLE;
        }
        if (hasFailedExecution(agentTurn)) {
            return AnswerResolution.CAPABILITY_UNAVAILABLE;
        }
        if (hasRenderablePayload(agentTurn)) {
            boolean partial = agentTurn.getOutcome().orElseThrow().getTaskOutcomes().stream()
                    .anyMatch(outcome -> outcome.getResolution()
                            == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED
                            || !outcome.hasRenderablePayload());
            return partial ? AnswerResolution.PARTIALLY_ANSWERED : AnswerResolution.ANSWERED;
        }
        return resolution(agentTurn);
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
        if (!hasFailedExecution(agentTurn)
                && hasValidPreset(request, content) && !hasRenderablePayload(agentTurn)) {
            List<ConversationAnswerBlock> blocks = presetBlocks(request, content);
            if (!blocks.isEmpty()) {
                return blocks;
            }
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
            return AnswerConstructionMode.GENERAL_MODEL;
        }
        return hasRenderablePayload(agentTurn)
                || hasProjectedFallback(request, content, agentTurn)
                ? AnswerConstructionMode.EVIDENCE_COMPOSITION : AnswerConstructionMode.TEMPLATE;
    }

    private GenerationMode projectedGenerationMode(AgentTurnResult agentTurn) {
        return hasRenderableGeneralPayload(agentTurn)
                ? GenerationMode.MODEL : GenerationMode.DETERMINISTIC;
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
                            claim.getStatement(),
                            List.of(claim.getId()), claim.getDirectEvidenceIds()));
                }
                if (!blocks.isEmpty()) {
                    return List.copyOf(blocks);
                }
            }
        }
        return List.of();
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
            case PLAN_INVALIDATED -> AnswerResolution.NEEDS_CLARIFICATION;
        };
    }

    private String title(AgentTurnResult agentTurn) {
        return switch (agentTurn.getDisposition()) {
            case READY, PARTIAL_READY -> "处理结果";
            case CONFIRMATION_REQUIRED -> "请确认执行计划";
            case CLARIFICATION_REQUIRED -> "需要补充信息";
            case PLAN_INVALIDATED -> "计划需要重新生成";
            case BOUNDARY, REJECTED -> "无法处理此请求";
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
