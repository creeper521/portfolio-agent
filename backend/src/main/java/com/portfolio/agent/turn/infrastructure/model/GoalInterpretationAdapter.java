package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalProposalDecodeException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Goal 解析的模型端口适配器：把 GoalInterpretationInput 投影为受限 JSON 提示，
 * 经结构化传输调用所选模型，并按 GoalProposalCodec 的严格 schema 解码结果。
 *
 * <p>能力闸门在调用前置检查：所选模型不支持 TURN_INTERPRETATION 时，MODEL 选择
 * 抛 SelectedModelFailureException、NONE 选择抛不可用异常；超时以 TurnDeadline 与
 * 操作超时的较小者封顶。schema 解码失败按"模型返回无法安全采用"处理并上报诊断。</p>
 */
public final class GoalInterpretationAdapter implements GoalInterpretationPort {
    private final StructuredOutputGateway gateway;
    private final ObjectMapper mapper;
    private final GoalProposalCodec codec;
    private final String systemPrompt;
    private final int maxTokens;
    private final Duration timeout;
    private final ModelOutputDiagnostics outputDiagnostics;
    public GoalInterpretationAdapter(
            StructuredOutputGateway gateway, ObjectMapper mapper,
            GoalProposalCodec codec, String systemPrompt,
            int maxTokens, Duration timeout) {
        this(gateway, mapper, codec, systemPrompt, maxTokens, timeout,
                ModelOutputDiagnostics.none());
    }
    public GoalInterpretationAdapter(
            StructuredOutputGateway gateway, ObjectMapper mapper,
            GoalProposalCodec codec, String systemPrompt,
            int maxTokens, Duration timeout,
            ModelOutputDiagnostics outputDiagnostics) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.mapper = mapper; this.codec = codec;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens; this.timeout = timeout;
        this.outputDiagnostics = java.util.Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }
    /**
     * 执行一次目标解析调用。
     *
     * <p>先做能力与截止时间前置检查，再调用传输；只有传输确实开始或返回结果后
     * 才标记尝试阶段。调用前安全拒绝保持未尝试。传输失败与 schema 解码失败分别
     * 映射为所选模型失败异常（携带稳定失败类别），其余输入投影异常映射为不可用
     * 异常。</p>
     *
     * @throws SelectedModelFailureException 所选模型不可用、被限流或返回无法安全采用的结果
     * @throws GoalInterpretationUnavailableException 输入投影失败或模型能力整体不可用
     */
    @Override public GoalInterpretationResult interpret(
            GoalInterpretationInput input, TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (!modelExecution.getSnapshot().supports(
                ModelCapability.TURN_INTERPRETATION)) {
            if (modelExecution.getSnapshot().getKind()
                    == com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException.unavailableBeforeAttempt();
            }
            throw new GoalInterpretationUnavailableException();
        }
        StructuredModelRequest request = new StructuredModelRequest(
                com.portfolio.agent.infrastructure.model.policy.ModelOperation
                        .TURN_INTERPRETATION,
                systemPrompt, prompt(input), maxTokens, 0.0d,
                deadline.cappedAt(timeout));
        if (request.deadline().isExpired()) {
            throw SelectedModelFailureException
                    .temporarilyUnavailableBeforeAttempt();
        }
        StructurallyValidatedOutput output;
        try {
            output = gateway.execute(
                    modelExecution.getRequiredBinding(), request,
                    new GoalProviderDraftCompiler(input),
                    GoalInterpretationAdapter::classifyProviderSchemaFailure);
        } catch (StructuredModelFailure failure) {
            SelectedModelFailureException selected =
                    SelectedModelFailureException.from(failure);
            markAttemptedWhenObserved(modelExecution, selected);
            throw selected;
        } catch (StructuredOutputValidationException failure) {
            modelExecution.markAttempted(
                    ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
            outputDiagnostics.rejected(
                    "GOAL_INTERPRETATION", diagnosticLayer(failure),
                    failure.getDiagnosticReason());
            throw SelectedModelFailureException.invalidResponse(failure);
        }
        modelExecution.markAttempted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        try {
            return codec.decode(output.jsonTree(), input);
        } catch (GoalProposalDecodeException failure) {
            outputDiagnostics.rejected(
                    "GOAL_INTERPRETATION", ModelOutputDiagnostics.Layer.SCHEMA,
                    failure.getReason().name());
            throw SelectedModelFailureException.invalidResponse(failure);
        } catch (IllegalArgumentException failure) {
            outputDiagnostics.rejected(
                    "GOAL_INTERPRETATION", ModelOutputDiagnostics.Layer.SCHEMA);
            throw SelectedModelFailureException.invalidResponse(failure);
        }
    }

    private static void markAttemptedWhenObserved(
            ResolvedModelExecution modelExecution,
            SelectedModelFailureException failure) {
        if (failure.isAttempted()) {
            modelExecution.markAttempted(
                    ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        }
    }

    private ModelOutputDiagnostics.Layer diagnosticLayer(
            StructuredOutputValidationException failure) {
        return switch (failure.getStage()) {
            case PROVIDER_DRAFT_SCHEMA ->
                    ModelOutputDiagnostics.Layer.PROVIDER_DRAFT_SCHEMA;
            case DETERMINISTIC_COMPILER ->
                    ModelOutputDiagnostics.Layer.DETERMINISTIC_COMPILER;
            case CANONICAL_SCHEMA ->
                    ModelOutputDiagnostics.Layer.CANONICAL_SCHEMA;
            case UNCLASSIFIED_SCHEMA -> ModelOutputDiagnostics.Layer.SCHEMA;
        };
    }

    private static StructuredOutputValidationException
            classifyProviderSchemaFailure(
                    JsonNode tree,
                    StructuredOutputValidationException genericFailure) {
        JsonNode kind = tree.get("kind");
        if (kind != null && (!kind.isTextual()
                || !("SEMANTIC_ROUTE".equals(kind.textValue())
                || "CONVERSATIONAL".equals(kind.textValue())))) {
            return goalSchemaFailure(
                    StructuredOutputValidationException.Reason
                            .UNSUPPORTED_ROOT_KIND,
                    genericFailure);
        }
        if ("SEMANTIC_ROUTE".equals(tree.path("kind").asText())
                && "NEEDS_CLARIFICATION".equals(
                        tree.path("route").asText())) {
            JsonNode clarification = tree.get("clarification");
            if (clarification != null && clarification.isObject()) {
                JsonNode blockedGoal = clarification.get("blockedGoal");
                if (blockedGoal == null || !blockedGoal.isObject()) {
                    return goalSchemaFailure(
                            StructuredOutputValidationException.Reason
                                    .CLARIFICATION_BLOCKED_GOAL_REQUIRED,
                            genericFailure);
                }
            }
        }
        return genericFailure;
    }

    private static StructuredOutputValidationException goalSchemaFailure(
            StructuredOutputValidationException.Reason reason,
            StructuredOutputValidationException genericFailure) {
        return new StructuredOutputValidationException(
                reason, reason.name(), genericFailure.getStage());
    }
    /**
     * 把解析输入投影为受限 JSON：只包含允许的目标类别、路由、候选与已审阅主体
     * 描述，不包含任何由 Adapter 重复声明的 schema 版本；结构合同只从
     * 本 Turn 冻结的 OperationBinding 解析。
     */
    private String prompt(GoalInterpretationInput input) {
        try {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("currentInput", input.getUserText());
            projection.put("recentConversation", input.getRecentMessages());
            projection.put("recentSemanticState", input.getRecentSemanticState());
            projection.put("interpretationMode", input.getInterpretationMode());
            projection.put("discussionState", input.getDiscussionState());
            projection.put("allowedGoalKinds", input.getAllowedGoalKinds());
            projection.put("allowedRoutes", input.getAllowedRoutes());
            projection.put("allowedRecommendationConstraints",
                    input.getAllowedRecommendationConstraints());
            projection.put("defaultSubject", input.getDefaultSubject() == null
                    ? null : subject(input.getDefaultSubject()));
            projection.put("audienceProfile", input.getAudienceProfile());
            projection.put("publicSubjects", input.getPublicSubjects().stream().map(subject -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("kind", subject.getKind()); value.put("reference", subject.getReference());
                value.put("reviewedLabel", subject.getLabel());
                value.put("reviewedAliases", subject.getReviewedAliases()); return value;
            }).toList());
            projection.put("lockedSubject", input.getLockedSubject() == null
                    ? null : subject(input.getLockedSubject()));
            projection.put("routeCandidates", input.getRouteCandidates().stream()
                    .map(candidate -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("candidateKey", candidate.getCandidateKey());
                        value.put("kind", candidate.getKind());
                        value.put("reference", candidate.getReference());
                        value.put("reviewedLabel", candidate.getLabel());
                        value.put("reviewedAliases", candidate.getReviewedAliases());
                        return value;
                    }).toList());
            return mapper.writeValueAsString(projection);
        } catch (Exception failure) {
            throw new GoalInterpretationUnavailableException(failure);
        }
    }

    private Map<String, Object> subject(
            GoalInterpretationInput.PublicSubjectDescriptor subject) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", subject.getKind());
        value.put("reference", subject.getReference());
        value.put("reviewedLabel", subject.getLabel());
        value.put("reviewedAliases", subject.getReviewedAliases());
        return value;
    }
}
