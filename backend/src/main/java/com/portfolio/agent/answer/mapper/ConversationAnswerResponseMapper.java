package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.dto.response.AgentTurnResponse;
import com.portfolio.agent.answer.dto.response.AgentTurnOutcomeResponse;
import com.portfolio.agent.answer.dto.response.ClarificationResponse;
import com.portfolio.agent.answer.dto.response.CompletedTaskResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerBlockResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.dto.response.ConversationSuggestedQuestionResponse;
import com.portfolio.agent.answer.dto.response.DisplayPlanResponse;
import com.portfolio.agent.answer.dto.response.PlanConfirmationResponse;
import com.portfolio.agent.answer.dto.response.PlanChangeResponse;
import com.portfolio.agent.answer.dto.response.InvalidatedPlanReferenceResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationContextResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationItemResponse;
import com.portfolio.agent.answer.dto.response.TaskSummaryResponse;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.service.ClarificationRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@Component
public final class ConversationAnswerResponseMapper {

    public ConversationAnswerResponse toResponse(ConversationAnswerResult result) {
        AgentTurnResult agentTurn = result.getAgentTurn();
        return new ConversationAnswerResponse(
                result.getTurnId(),
                result.getContentVersion(),
                result.getIntent(),
                publicScope(result.getAnswerScope()),
                publicResolution(result.getResolution(), agentTurn),
                result.getTitle(),
                topLevelBlocks(result, agentTurn),
                result.getSuggestedQuestions().stream()
                        .map(ConversationSuggestedQuestionResponse::from)
                        .toList(),
                result.isDegraded(),
                result.getConstructionMode(),
                result.getIntentSource(),
                result.getEvidenceState(),
                result.getNoticeCode(),
                result.getProgress().getCoveredTopics(),
                result.getProgress().getStage(),
                publicRecommendation(result, agentTurn),
                result.isContextVersionUpdated(),
                result.getQuestionPresetId(),
                result.getContractVersion(),
                result.getSummary(),
                agentTurn == null ? null : toAgentTurnResponse(agentTurn, result));
    }

    private ConversationAnswerBlockResponse toBlockResponse(ConversationAnswerBlock block) {
        return new ConversationAnswerBlockResponse(
                block.getSourceScope(),
                block.getSectionType(),
                block.getTitle(),
                block.getContent(),
                block.getClaimIds(),
                block.getEvidenceIds());
    }

    private List<ConversationAnswerBlockResponse> topLevelBlocks(
            ConversationAnswerResult result, AgentTurnResult agentTurn) {
        if (agentTurn == null || agentTurn.getOutcome().isEmpty()) {
            return result.getBlocks().stream().map(this::toBlockResponse).toList();
        }
        List<ConversationAnswerBlockResponse> mapped = new ArrayList<>();
        Map<String, SemanticTask> tasksById = indexTasks(agentTurn.getPlan());
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            if (!outcome.hasRenderablePayload()) {
                continue;
            }
            SemanticTask task = tasksById.get(outcome.getTaskId());
            if (task == null) {
                continue;
            }
            TaskResultPayload payload = outcome.getResultPayload().orElseThrow();
            TaskResultProvenance provenance = outcome.getProvenance().orElseGet(() ->
                    TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()));
            if (payload instanceof TaskResultPayload.SectionResultPayload section) {
                appendTopLevelBlocks(mapped, sourceScope(task), section.getBlocks(),
                        provenance.getClaimIds(), provenance.getEvidenceIds());
            } else if (payload instanceof TaskResultPayload.SynthesisResultPayload synthesis) {
                appendTopLevelBlocks(mapped, sourceScope(task), synthesis.getBlocks(),
                        synthesis.getProvenance().getClaimIds(), synthesis.getProvenance().getEvidenceIds());
            }
        }
        // A semantic task may complete without a renderable section payload
        // (for example a reviewed preset whose deterministic claims are
        // projected by the runtime). Preserve that safe top-level projection
        // instead of dropping it merely because an outcome object exists.
        if (mapped.isEmpty() && !result.getBlocks().isEmpty()) {
            return result.getBlocks().stream().map(this::toBlockResponse).toList();
        }
        return List.copyOf(mapped);
    }

    private static void appendTopLevelBlocks(
            List<ConversationAnswerBlockResponse> target,
            ConversationSourceScope sourceScope,
            List<String> contents,
            List<String> claimIds,
            List<String> evidenceIds) {
        for (String content : contents) {
            target.add(new ConversationAnswerBlockResponse(sourceScope, content, claimIds, evidenceIds));
        }
    }

    private PortfolioRecommendationResponse publicRecommendation(
            ConversationAnswerResult result, AgentTurnResult agentTurn) {
        if (agentTurn != null) {
            if (countRenderableRecommendations(agentTurn) != 1) {
                return null;
            }
            if (result.getPortfolioRecommendation() != null) {
                return PortfolioRecommendationResponse.from(result.getPortfolioRecommendation());
            }
            return singleRecommendationProjection(agentTurn)
                    .map(this::toRecommendationResponse)
                    .orElse(null);
        }
        return result.getPortfolioRecommendation() == null ? null
                : PortfolioRecommendationResponse.from(result.getPortfolioRecommendation());
    }

    private Optional<TaskResultPayload.RecommendationProjection> singleRecommendationProjection(
            AgentTurnResult agentTurn) {
        if (agentTurn.getOutcome().isEmpty()) {
            return Optional.empty();
        }
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            if (outcome.hasRenderablePayload()
                    && outcome.getResultPayload().orElseThrow()
                    instanceof TaskResultPayload.RecommendationResultPayload recommendation
                    && recommendation.getProjection() != null) {
                return Optional.of(recommendation.getProjection());
            }
        }
        return Optional.empty();
    }

    private PortfolioRecommendationResponse toRecommendationResponse(
            TaskResultPayload.RecommendationProjection projection) {
        PortfolioRecommendationContextResponse context = new PortfolioRecommendationContextResponse(
                projection.getRecommendationBatchId(),
                projection.getContentVersion(),
                projection.getCareerTrack(),
                projection.getAudienceRole(),
                projection.getCapabilityCodes(),
                projection.getRequestedSize(),
                projection.getSelectedPortfolioIds());
        List<PortfolioRecommendationItemResponse> items = projection.getItems().stream()
                .map(item -> new PortfolioRecommendationItemResponse(
                        item.getPortfolioId(), item.getTitle(), item.getRoute(),
                        item.getMatchReasons(), item.getEvidenceIds()))
                .toList();
        return new PortfolioRecommendationResponse(
                projection.getRecommendationBatchId(), context, items,
                projection.getSatisfiedConstraints(), projection.getUnsatisfiedConstraints());
    }

    private int countRenderableRecommendations(AgentTurnResult agentTurn) {
        if (agentTurn.getOutcome().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            if (outcome.hasRenderablePayload()
                    && outcome.getResultPayload().orElseThrow()
                    instanceof TaskResultPayload.RecommendationResultPayload) {
                count++;
            }
        }
        return count;
    }

    private AgentTurnResponse toAgentTurnResponse(AgentTurnResult agentTurn, ConversationAnswerResult result) {
        SemanticTurnOutcome outcomeValue = agentTurn.getOutcome().orElse(null);
        DisplayPlanResponse plan = agentTurn.getPlan()
                .map(value -> toDisplayPlan(value, outcomeValue)).orElse(null);
        PlanChangeResponse planChange = toPlanChange(agentTurn);
        PlanConfirmationResponse confirmation = agentTurn.getPlanConfirmation()
                .map(value -> toPlanConfirmation(value, agentTurn.getPlan().orElse(null))).orElse(null);
        ClarificationResponse clarification = agentTurn.getClarification()
                .map(this::toClarification).orElse(null);
        TaskSummaryResponse summary = outcomeValue == null ? null
                : toTaskSummary(agentTurn.getPlan().orElse(null), outcomeValue);
        AgentTurnOutcomeResponse outcome = outcomeValue == null ? null
                : new AgentTurnOutcomeResponse(outcomeValue.getPlanOutcome(), summary);
        List<CompletedTaskResponse> completed = outcomeValue == null ? null
                : toCompletedTasks(agentTurn.getPlan().orElse(null), outcomeValue, result,
                        countRenderableRecommendations(agentTurn));
        String reasonCode = agentTurn.getInvalidationReason().map(Enum::name)
                .orElseGet(() -> agentTurn.getReasonCodes().stream().sorted().findFirst().orElse(null));
        AgentTurnResult.Disposition wireDisposition = agentTurn.getDisposition()
                == AgentTurnResult.Disposition.PLAN_INVALIDATED
                ? AgentTurnResult.Disposition.REJECTED : agentTurn.getDisposition();
        if (wireDisposition != AgentTurnResult.Disposition.READY
                && wireDisposition != AgentTurnResult.Disposition.PARTIAL_READY) {
            outcome = null;
            completed = null;
        }
        return new AgentTurnResponse(wireDisposition, plan, planChange, confirmation, clarification,
                outcome, completed);
    }

    private DisplayPlanResponse toDisplayPlan(SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        Map<String, String> displayIndexes = displayIndexes(plan);
        List<DisplayPlanResponse.Task> tasks = new ArrayList<>();
        for (SemanticTask task : plan.getTasks()) {
            tasks.add(new DisplayPlanResponse.Task(
                    displayIndexes.get(task.getTaskId()), task.getGoalLabel(), task.getSourceDomain(),
                    dependencySummary(plan, task.getTaskId(), displayIndexes)));
        }
        Integer executable = outcome == null ? null : (int) outcome.getTaskOutcomes().stream()
                .filter(value -> value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.BLOCKED
                        && value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.CANCELLED
                        && value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.FAILED)
                .count();
        return new DisplayPlanResponse(plan.getTasks().size(), executable, tasks, constraints(plan));
    }

    private static List<String> constraints(SemanticTurnPlan plan) {
        List<String> labels = new ArrayList<>();
        for (com.portfolio.agent.answer.routing.domain.PlanExclusion exclusion : plan.getExclusions()) {
            String label = switch (exclusion.getType()) {
                case OUTPUT -> "限制输出范围";
                case SUBJECT -> "限制主体范围";
                case DIMENSION -> "限制比较维度";
                case CONSTRAINT -> "遵循安全约束";
            };
            if (!labels.contains(label)) labels.add(label);
        }
        return List.copyOf(labels);
    }

    private PlanChangeResponse toPlanChange(AgentTurnResult agentTurn) {
        if (agentTurn.getInvalidationReason().isEmpty()) return null;
        PlanConfirmation.PlanInvalidationReason reason = agentTurn.getInvalidationReason().orElseThrow();
        String summary;
        String label;
        switch (reason) {
            case CONTENT_VERSION_CHANGED -> { summary = "公开内容已更新，需要重新生成计划"; label = "内容版本已更新"; }
            case SUBJECT_REFERENCE_INVALIDATED -> { summary = "主体引用已变化，需要重新生成计划"; label = "主体引用已变化"; }
            case CAPABILITY_SET_CHANGED -> { summary = "可用能力已变化，需要重新生成计划"; label = "能力集合已变化"; }
            case PLAN_CONFIRMATION_EXPIRED -> { summary = "计划确认已过期，需要重新生成计划"; label = "确认已过期"; }
            default -> { summary = "计划已失效，需要重新生成"; label = "计划已失效"; }
        }
        InvalidatedPlanReferenceResponse reference = agentTurn.getInvalidatedPlanReference()
                .map(value -> new InvalidatedPlanReferenceResponse(value.getPlanId(), value.getPlanFingerprint()))
                .orElse(null);
        return new PlanChangeResponse(summary, List.of(label), reference);
    }

    private static String dependencySummary(
            SemanticTurnPlan plan, String taskId, Map<String, String> displayIndexes) {
        List<String> upstream = new ArrayList<>();
        for (TaskDependency dependency : plan.getDependencies()) {
            if (taskId.equals(dependency.getToTaskId())) {
                upstream.add(displayIndexes.get(dependency.getFromTaskId()));
            }
        }
        return upstream.isEmpty() ? null : "需要 " + String.join("、", upstream) + " 先完成";
    }

    private PlanConfirmationResponse toPlanConfirmation(
            PlanConfirmation.Challenge challenge, SemanticTurnPlan plan) {
        return new PlanConfirmationResponse(challenge.getConfirmationId(), challenge.getExpiresAt().toString(),
                challenge.getConfirmationPlan(), challenge.getPlanFingerprint(), challenge.getIntegrityToken(),
                plan == null ? List.of() : plan.getConfirmationPolicy().getTriggerCodes().stream()
                        .map(Enum::name).sorted().toList());
    }

    private ClarificationResponse toClarification(ClarificationRequest clarification) {
        List<ClarificationResponse.Field> fields = clarification.getFields().stream().map(field ->
                new ClarificationResponse.Field(field.getFieldKey(), field.getInputMode().name(),
                        field.getOptions().stream().map(option -> new ClarificationResponse.Option(
                                option.getValue(), option.getLabel())).toList(),
                        field.isRequired(), field.getAffectedGoalLabels())).toList();
        return new ClarificationResponse(clarification.getScope().name(), clarification.getPromptCode(),
                clarification.getPrompt(), fields, clarification.getBlockedTaskCount(),
                clarification.getContinuingTaskCount());
    }

    private TaskSummaryResponse toTaskSummary(SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        Map<String, SemanticTask> tasksById = indexTasks(Optional.ofNullable(plan));
        Map<String, String> indexes = plan == null ? Map.of() : displayIndexes(plan);
        List<TaskSummaryResponse.Item> items = new ArrayList<>();
        for (TaskOutcome taskOutcome : outcome.getTaskOutcomes()) {
            SemanticTask task = tasksById.get(taskOutcome.getTaskId());
            if (task != null) {
                items.add(new TaskSummaryResponse.Item(indexes.get(task.getTaskId()), task.getGoalLabel(),
                        publicTaskStatus(taskOutcome), task.getSourceDomain()));
            }
        }
        String mode = outcome.getPlanOutcome() == SemanticTurnOutcome.PlanOutcome.SUCCEEDED
                && outcome.getTaskOutcomes().size() == 1 ? "HIDDEN"
                : outcome.getPlanOutcome() == SemanticTurnOutcome.PlanOutcome.SUCCEEDED
                && outcome.getTaskOutcomes().size() <= 3 ? "COLLAPSED" : "EXPANDED";
        return new TaskSummaryResponse(mode, outcome.getTaskOutcomes().size(), outcome.getAnsweredCount(),
                outcome.getNotSupportedCount(), outcome.getEmptyCount(), outcome.getBlockedCount(),
                outcome.getFailedCount(), outcome.getCancelledCount(), outcome.getDegradedCount(), items);
    }

    private List<CompletedTaskResponse> toCompletedTasks(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome,
            ConversationAnswerResult result, int recommendationCount) {
        Map<String, SemanticTask> tasksById = indexTasks(Optional.ofNullable(plan));
        Map<String, String> indexes = plan == null ? Map.of() : displayIndexes(plan);
        List<CompletedTaskResponse> completed = new ArrayList<>();
        for (TaskOutcome outcomeItem : outcome.getTaskOutcomes()) {
            if (!outcomeItem.hasRenderablePayload()) {
                continue;
            }
            SemanticTask task = tasksById.get(outcomeItem.getTaskId());
            if (task != null) {
                completed.add(new CompletedTaskResponse(indexes.get(task.getTaskId()), task.getGoalLabel(),
                        task.getSourceDomain(), toResultPayload(
                                outcomeItem.getResultPayload().orElseThrow(), task, result, recommendationCount)));
            }
        }
        return List.copyOf(completed);
    }

    private CompletedTaskResponse.ResultPayload toResultPayload(
            TaskResultPayload payload, SemanticTask task,
            ConversationAnswerResult result, int recommendationCount) {
        if (payload instanceof TaskResultPayload.SectionResultPayload section) {
            TaskResultProvenance provenance = provenanceFor(task, payload, result);
            return new CompletedTaskResponse.ResultPayload("SECTION_RESULT",
                    toBlocks(section.getBlocks(), sourceScope(task), provenance.getClaimIds(), provenance.getEvidenceIds()),
                    null, null);
        }
        if (payload instanceof TaskResultPayload.RecommendationResultPayload recommendation) {
            TaskResultProvenance provenance = provenanceFor(task, payload, result);
            List<com.portfolio.agent.answer.dto.response.PortfolioRecommendationItemResponse> recommendations =
                    recommendation.getItems().stream().map(item ->
                            new com.portfolio.agent.answer.dto.response.PortfolioRecommendationItemResponse(
                                    item.getPortfolioId(),
                                    item.getTitle(),
                                    item.getRoute(),
                                    item.getMatchReasons(),
                                    item.getEvidenceIds()))
                            .toList();
            return new CompletedTaskResponse.ResultPayload("RECOMMENDATION_RESULT",
                    toBlocks(recommendation.getSupportingBlocks(), sourceScope(task),
                            provenance.getClaimIds(), provenance.getEvidenceIds()),
                    recommendations, null);
        }
        TaskResultPayload.SynthesisResultPayload synthesis = (TaskResultPayload.SynthesisResultPayload) payload;
        return new CompletedTaskResponse.ResultPayload("SYNTHESIS_RESULT",
                toBlocks(synthesis.getBlocks(), sourceScope(task), synthesis.getProvenance().getClaimIds(),
                        synthesis.getProvenance().getEvidenceIds()), null,
                synthesis.getProvenance().getOriginDomains().stream().sorted().toList());
    }

    private List<ConversationAnswerBlockResponse> toBlocks(
            List<String> contents, ConversationSourceScope sourceScope,
            List<String> claimIds, List<String> evidenceIds) {
        return contents.stream().map(value -> new ConversationAnswerBlockResponse(
                sourceScope, value, claimIds, evidenceIds)).toList();
    }

    private TaskResultProvenance provenanceFor(
            SemanticTask task, TaskResultPayload payload, ConversationAnswerResult result) {
        if (result.getAgentTurn() != null) {
            for (TaskOutcome value : result.getAgentTurn().getOutcome()
                    .map(SemanticTurnOutcome::getTaskOutcomes).orElse(List.of())) {
                if (value.getTaskId().equals(task.getTaskId())
                        && value.getResultPayload().orElse(null) == payload) {
                    return value.getProvenance().orElseGet(() ->
                            TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()));
                }
            }
        }
        return TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of());
    }

    private static String publicTaskStatus(TaskOutcome outcome) {
        if (outcome.hasRenderablePayload()) {
            return "COMPLETED";
        }
        return switch (outcome.getExecutionStatus()) {
            case BLOCKED -> "BLOCKED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case NOT_STARTED, RUNNING, SUCCEEDED -> switch (outcome.getResolution()) {
                case NOT_SUPPORTED, CAPABILITY_UNAVAILABLE -> "NOT_SUPPORTED";
                case EMPTY -> "EMPTY";
                case REJECTED, BOUNDARY, NOT_APPLICABLE -> "NOT_SUPPORTED";
                case ANSWERED -> "FAILED";
            };
        };
    }

    private static Map<String, SemanticTask> indexTasks(Optional<SemanticTurnPlan> plan) {
        if (plan.isEmpty()) {
            return Map.of();
        }
        Map<String, SemanticTask> indexed = new LinkedHashMap<>();
        for (SemanticTask task : plan.orElseThrow().getTasks()) {
            indexed.put(task.getTaskId(), task);
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, String> displayIndexes(SemanticTurnPlan plan) {
        Map<String, String> indexes = new LinkedHashMap<>();
        for (int index = 0; index < plan.getTasks().size(); index++) {
            indexes.put(plan.getTasks().get(index).getTaskId(), String.format("%02d", index + 1));
        }
        return Map.copyOf(indexes);
    }

    private static ConversationSourceScope sourceScope(SemanticTask task) {
        return task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                ? ConversationSourceScope.PORTFOLIO : ConversationSourceScope.GENERAL;
    }

    private static AnswerResolution publicResolution(
            AnswerResolution resolution, AgentTurnResult agentTurn) {
        if (agentTurn != null && agentTurn.isConfirmationRequired()) {
            return agentTurn.isRequestUsesStpV1()
                    ? AnswerResolution.AWAITING_CONFIRMATION
                    : AnswerResolution.NEEDS_CLARIFICATION;
        }
        return resolution == AnswerResolution.BOUNDARY
                ? AnswerResolution.NEEDS_CLARIFICATION
                : resolution;
    }

    private static ConversationAnswerScope publicScope(ConversationAnswerScope scope) {
        return switch (scope) {
            case CONVERSATION -> ConversationAnswerScope.GLOBAL;
            case HYBRID -> ConversationAnswerScope.MIXED;
            case GENERAL, PORTFOLIO, GLOBAL, MIXED -> scope;
        };
    }
}
