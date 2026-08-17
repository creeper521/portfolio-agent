package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.AnswerSourceComposition;
import com.portfolio.agent.answer.domain.AnswerSupportKind;
import com.portfolio.agent.answer.domain.PublicResultItemId;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.dto.response.AgentTurnResponse;
import com.portfolio.agent.answer.dto.response.AgentTurnOutcomeResponse;
import com.portfolio.agent.answer.dto.response.ClarificationResponse;
import com.portfolio.agent.answer.dto.response.CompletedTaskResponse;
import com.portfolio.agent.answer.dto.response.TaskCompositionResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerBlockResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.dto.response.ConversationSuggestedQuestionResponse;
import com.portfolio.agent.answer.dto.response.DisplayPlanResponse;
import com.portfolio.agent.answer.dto.response.ExecutionDisplayPlanResponse;
import com.portfolio.agent.answer.dto.response.PublicSourceReferenceResponse;
import com.portfolio.agent.answer.dto.response.AnswerBlockSupportResponse;
import com.portfolio.agent.answer.dto.response.StatementSupportReferenceResponse;
import com.portfolio.agent.answer.dto.response.PublicSourceCatalogEntryResponse;
import com.portfolio.agent.answer.dto.response.PlanConfirmationResponse;
import com.portfolio.agent.answer.dto.response.PlanChangeResponse;
import com.portfolio.agent.answer.dto.response.InvalidatedPlanReferenceResponse;
import com.portfolio.agent.answer.dto.response.PendingPlanReferenceResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationItemResponse;
import com.portfolio.agent.answer.dto.response.TaskSummaryResponse;
import com.portfolio.agent.answer.dto.response.TaskSupportSummaryResponse;
import com.portfolio.agent.answer.dto.response.ContinuationContextResponse;
import com.portfolio.agent.answer.dto.response.SubjectReferenceResponse;
import com.portfolio.agent.answer.dto.response.ContextInvalidationResponse;
import com.portfolio.agent.answer.dto.response.ContextResolutionResponse;
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
import com.portfolio.agent.answer.intelligence.execution.service.ExecutionDisplayPlanProjector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
@Component
public final class ConversationAnswerResponseMapper {

    private final ExecutionDisplayPlanProjector executionProjector = new ExecutionDisplayPlanProjector();

    public ConversationAnswerResponse toResponse(ConversationAnswerResult result) {
        return toResponse(result, null);
    }

    public ConversationAnswerResponse toResponse(
            ConversationAnswerResult result, com.portfolio.agent.answer.dto.response.ConversationResponse conversation) {
        return toResponse(result, conversation, Map.of());
    }

    public ConversationAnswerResponse toResponse(
            ConversationAnswerResult result,
            com.portfolio.agent.answer.dto.response.ConversationResponse conversation,
            Map<String, ContextHandle> contextHandles) {
        AgentTurnResult agentTurn = result.getAgentTurn();
        boolean stpV1 = agentTurn == null || agentTurn.isRequestUsesStpV1();
        AnswerResolution resolution = publicResolution(result.getResolution(), agentTurn);
        boolean answerLike = resolution == AnswerResolution.ANSWERED
                || resolution == AnswerResolution.PARTIALLY_ANSWERED;
        List<ConversationAnswerBlockResponse> mappedBlocks = topLevelBlocks(result, agentTurn, stpV1);
        List<ConversationAnswerBlockResponse> blocks = answerLike
                ? mappedBlocks : redactSources(mappedBlocks);
        return new ConversationAnswerResponse(
                result.getTurnId(),
                result.getContentVersion(),
                result.getIntent(),
                publicScope(result.getAnswerScope()),
                resolution,
                result.getTitle(),
                blocks,
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
                publicRecommendation(result, agentTurn, stpV1),
                result.isContextVersionUpdated(),
                result.getQuestionPresetId(),
                result.getContractVersion(),
                result.getSummary(),
                agentTurn == null ? null : toAgentTurnResponse(agentTurn, result, contextHandles),
                "ANSWER", conversation,
                stpV1 || !answerLike ? null : sourceComposition(blocks),
                stpV1 || !answerLike ? List.of() : publicSourceCatalog(blocks),
                stpV1 ? null : agentTurn == null ? null : agentTurn.getContextInvalidation()
                        .map(value -> new ContextInvalidationResponse(value.getReasonCode(),
                                value.getRecoveryAction(), value.getContextType(),
                                value.getCurrentContentVersion())).orElse(null),
                stpV1 ? null : agentTurn == null ? null : agentTurn.getContextResolution()
                        .map(value -> new ContextResolutionResponse(value.getMode(), value.getContextType(),
                                value.getCurrentContentVersion())).orElse(null));
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

    private static List<ConversationAnswerBlockResponse> redactSources(
            List<ConversationAnswerBlockResponse> blocks) {
        return blocks.stream().map(block -> new ConversationAnswerBlockResponse(
                block.getSourceScope(), block.getSectionType(), block.getTitle(), block.getContent(),
                List.of(), List.of(), List.of())).toList();
    }

    private ConversationAnswerBlockResponse toBlockResponse(
            ConversationAnswerBlock block, boolean stpV1) {
        return toBlockResponse(block, stpV1, false);
    }

    private ConversationAnswerBlockResponse toBlockResponse(
            ConversationAnswerBlock block, boolean stpV1, boolean includePortfolioBlock) {
        if (stpV1) {
            return toBlockResponse(block);
        }
        SemanticRoutingTypes.TaskSourceDomain domain = block.getSourceScope()
                == ConversationSourceScope.PORTFOLIO
                ? SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                : SemanticRoutingTypes.TaskSourceDomain.GENERAL;
        if (domain == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO && !includePortfolioBlock) {
            return null;
        }
        AnswerSupportKind kind = domain == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                ? AnswerSupportKind.VERIFIED_PUBLIC_EVIDENCE : AnswerSupportKind.GENERAL_KNOWLEDGE;
        List<StatementSupportReferenceResponse> statements = block.getClaimIds().stream()
                .map(id -> new StatementSupportReferenceResponse(id, null, List.of(), null)).toList();
        AnswerBlockSupportResponse support = new AnswerBlockSupportResponse(
                kind, statements, List.of(), List.of(), null);
        return new ConversationAnswerBlockResponse(
                stableId("block", block.getContent(), block.getClaimIds(), block.getEvidenceIds()),
                domain, block.getSourceScope(), block.getSectionType(), block.getTitle(), block.getContent(),
                block.getClaimIds(), block.getEvidenceIds(), List.of(), support);
    }

    private List<ConversationAnswerBlockResponse> topLevelBlocks(
            ConversationAnswerResult result, AgentTurnResult agentTurn, boolean stpV1) {
        // stp-v2 的普通 Portfolio 内容由 completedTasks 承载；但 preset 的答案是
        // 按已审核 claim 合同投影的权威正文，不能被自由文本任务的章节替换或过滤掉。
        if (!stpV1 && result.getQuestionPresetId() != null && !result.getBlocks().isEmpty()) {
            return deduplicatePublicReferences(result.getBlocks().stream()
                    .map(block -> toBlockResponse(block, false, true))
                    .filter(Objects::nonNull).toList(), false);
        }
        if (agentTurn == null || agentTurn.getOutcome().isEmpty()) {
            return deduplicatePublicReferences(result.getBlocks().stream()
                    .map(block -> toBlockResponse(block, stpV1))
                    .filter(Objects::nonNull).toList(), stpV1);
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
            if (outcome.getResultPayload().isEmpty() && outcome.getContribution().isPresent()) {
                mapped.addAll(toContributionBlocks(outcome.getContribution().orElseThrow(), task, stpV1));
                continue;
            }
            TaskResultPayload payload = outcome.getResultPayload().orElseThrow();
            TaskResultProvenance provenance = outcome.getProvenance().orElseGet(() ->
                    TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()));
            if (payload instanceof TaskResultPayload.SectionResultPayload section) {
                mapped.addAll(toSectionBlocks(section, task, provenance, stpV1));
            } else if (payload instanceof TaskResultPayload.SynthesisResultPayload synthesis) {
                appendTopLevelBlocks(mapped, task, synthesis.getBlocks(), synthesis.getProvenance(), stpV1);
            }
        }
        // A semantic task may complete without a renderable section payload
        // (for example a reviewed preset whose deterministic claims are
        // projected by the runtime). Preserve that safe top-level projection
        // instead of dropping it merely because an outcome object exists.
        if (mapped.isEmpty() && !result.getBlocks().isEmpty()) {
            return deduplicatePublicReferences(
                    result.getBlocks().stream().map(block -> toBlockResponse(block, stpV1))
                            .filter(Objects::nonNull).toList(), stpV1);
        }
        return deduplicatePublicReferences(mapped, stpV1);
    }

    private static List<ConversationAnswerBlockResponse> deduplicatePublicReferences(
            List<ConversationAnswerBlockResponse> blocks, boolean legacyProjection) {
        if (!legacyProjection) {
            return List.copyOf(blocks);
        }
        java.util.Set<String> seenReferenceKeys = new java.util.LinkedHashSet<>();
        List<ConversationAnswerBlockResponse> deduplicated = new ArrayList<>();
        for (ConversationAnswerBlockResponse block : blocks) {
            List<PublicSourceReferenceResponse> references = block.getSourceReferences().stream()
                    .filter(reference -> seenReferenceKeys.add(reference.getReferenceKey()))
                    .toList();
            deduplicated.add(new ConversationAnswerBlockResponse(
                    block.getSourceScope(), block.getSectionType(), block.getTitle(), block.getContent(),
                    block.getClaimIds(), block.getEvidenceIds(), references));
        }
        return List.copyOf(deduplicated);
    }

    private List<ConversationAnswerBlockResponse> toContributionBlocks(
            com.portfolio.agent.answer.domain.GroundedAnswerContribution contribution,
            SemanticTask task,
            boolean stpV1) {
        List<PublicSourceReferenceResponse> references = contribution.getSourceReferences().stream()
                .map(PublicSourceReferenceResponse::from).toList();
        return contribution.getSupportedStatements().stream()
                .map(statement -> toSupportedBlock(task, statement, List.of(), List.of(), references,
                        TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()), stpV1,
                        null, null))
                .filter(Objects::nonNull)
                .toList();
    }

    private void appendTopLevelBlocks(
            List<ConversationAnswerBlockResponse> target,
            SemanticTask task,
            List<String> contents,
            TaskResultProvenance provenance,
            boolean stpV1) {
        for (String content : contents) {
            ConversationAnswerBlockResponse block = toSupportedBlock(task, content, provenance.getClaimIds(),
                    provenance.getEvidenceIds(), List.of(), provenance, stpV1, null, null);
            if (block != null) {
                target.add(block);
            }
        }
    }

    private PortfolioRecommendationResponse publicRecommendation(
            ConversationAnswerResult result, AgentTurnResult agentTurn, boolean stpV1) {
        if (agentTurn != null) {
            if (countRenderableRecommendations(agentTurn) != 1) {
                return null;
            }
            if (result.getPortfolioRecommendation() != null) {
                return stpV1
                        ? PortfolioRecommendationResponse.from(result.getPortfolioRecommendation())
                        : toRecommendationResponse(result.getPortfolioRecommendation());
            }
            return singleRecommendationProjection(agentTurn)
                    .map(projection -> toRecommendationResponse(projection, stpV1))
                    .orElse(null);
        }
        return result.getPortfolioRecommendation() == null ? null
                : PortfolioRecommendationResponse.from(result.getPortfolioRecommendation());
    }

    private PortfolioRecommendationResponse toRecommendationResponse(
            com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation recommendation) {
        return new PortfolioRecommendationResponse(
                recommendation.getRecommendationBatchId(),
                recommendation.getItems().stream().map(item ->
                        new PortfolioRecommendationItemResponse(
                                item.getPortfolioId(), item.getTitle(), item.getRoute(),
                                item.getMatchReasons(), item.getEvidenceIds(), List.of(),
                                stableId("result-item", item.getPortfolioId(),
                                        item.getMatchReasons(), item.getEvidenceIds()),
                                recommendation.getItems().indexOf(item) + 1,
                                subjectReference(item.getRoute(), item.getPortfolioId())))
                        .toList(),
                recommendation.getSatisfiedConstraints(), recommendation.getUnsatisfiedConstraints());
    }

    private Optional<TaskResultPayload.RecommendationProjection> singleRecommendationProjection(
            AgentTurnResult agentTurn) {
        if (agentTurn.getOutcome().isEmpty()) {
            return Optional.empty();
        }
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            if (outcome.hasRenderablePayload() && outcome.getResultPayload().isPresent()
                    && outcome.getResultPayload().orElseThrow()
                    instanceof TaskResultPayload.RecommendationResultPayload recommendation
                    && recommendation.getProjection() != null) {
                return Optional.of(recommendation.getProjection());
            }
        }
        return Optional.empty();
    }

    private PortfolioRecommendationResponse toRecommendationResponse(
            TaskResultPayload.RecommendationProjection projection, boolean stpV1) {
        List<PortfolioRecommendationItemResponse> items = projection.getItems().stream()
                .map(item -> new PortfolioRecommendationItemResponse(
                        item.getPortfolioId(), item.getTitle(), item.getRoute(),
                        item.getMatchReasons(), item.getEvidenceIds(),
                        item.getSourceReferences().stream()
                                .map(PublicSourceReferenceResponse::from).toList(),
                        stpV1 ? null : stableId("result-item", item.getPortfolioId(),
                                item.getMatchReasons(), item.getEvidenceIds()),
                        stpV1 ? null : projection.getItems().indexOf(item) + 1,
                        stpV1 ? null : subjectReference(item.getRoute(), item.getPortfolioId())))
                .toList();
        return new PortfolioRecommendationResponse(
                projection.getRecommendationBatchId(), items,
                projection.getSatisfiedConstraints(), projection.getUnsatisfiedConstraints());
    }

    private int countRenderableRecommendations(AgentTurnResult agentTurn) {
        if (agentTurn.getOutcome().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TaskOutcome outcome : agentTurn.getOutcome().orElseThrow().getTaskOutcomes()) {
            if (outcome.hasRenderablePayload() && outcome.getResultPayload().isPresent()
                    && outcome.getResultPayload().orElseThrow()
                    instanceof TaskResultPayload.RecommendationResultPayload) {
                count++;
            }
        }
        return count;
    }

    private AgentTurnResponse toAgentTurnResponse(
            AgentTurnResult agentTurn,
            ConversationAnswerResult result,
            Map<String, ContextHandle> contextHandles) {
        SemanticTurnOutcome outcomeValue = agentTurn.getOutcome().orElse(null);
        boolean stpV1 = agentTurn.isRequestUsesStpV1();
        DisplayPlanResponse plan = agentTurn.getPlan()
                .map(value -> toDisplayPlan(value, outcomeValue, stpV1)).orElse(null);
        PlanChangeResponse planChange = toPlanChange(agentTurn);
        PlanConfirmationResponse confirmation = agentTurn.getPlanConfirmation()
                .map(value -> toPlanConfirmation(value, agentTurn.getPlan().orElse(null))).orElse(null);
        ClarificationResponse clarification = agentTurn.getClarification()
                .map(this::toClarification).orElse(null);
        TaskSummaryResponse summary = outcomeValue == null ? null
                : toTaskSummary(agentTurn.getPlan().orElse(null), outcomeValue, stpV1);
        AgentTurnOutcomeResponse outcome = outcomeValue == null ? null
                : new AgentTurnOutcomeResponse(outcomeValue.getPlanOutcome(), summary);
        List<CompletedTaskResponse> completed = outcomeValue == null ? null
                : toCompletedTasks(agentTurn.getPlan().orElse(null), outcomeValue, result,
                        countRenderableRecommendations(agentTurn), contextHandles, stpV1);
        String reasonCode = agentTurn.getInvalidationReason().map(Enum::name)
                .orElseGet(() -> agentTurn.getReasonCodes().stream().sorted().findFirst().orElse(null));
        AgentTurnResult.Disposition wireDisposition = agentTurn.getDisposition()
                == AgentTurnResult.Disposition.PLAN_INVALIDATED
                ? AgentTurnResult.Disposition.REJECTED : agentTurn.getDisposition();
        ExecutionDisplayPlanResponse execution = outcomeValue == null || agentTurn.getPlan().isEmpty()
                ? null
                : ExecutionDisplayPlanResponse.from(
                        executionProjector.project(agentTurn.getPlan().orElseThrow(), outcomeValue));
        if (wireDisposition != AgentTurnResult.Disposition.READY
                && wireDisposition != AgentTurnResult.Disposition.PARTIAL_READY) {
            outcome = null;
            completed = null;
            execution = null;
        }
        return new AgentTurnResponse(stpV1 ? "stp-v1" : "stp-v2", wireDisposition, plan,
                planChange, confirmation, clarification, outcome, completed, execution);
    }

    private DisplayPlanResponse toDisplayPlan(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome, boolean stpV1) {
        Map<String, String> displayIndexes = displayIndexes(plan);
        List<DisplayPlanResponse.Task> tasks = new ArrayList<>();
        for (SemanticTask task : plan.getTasks()) {
            tasks.add(new DisplayPlanResponse.Task(
                    displayIndexes.get(task.getTaskId()), task.getGoalLabel(), task.getSourceDomain(),
                    dependencySummary(plan, task.getTaskId(), displayIndexes),
                    stpV1 ? null : task.getFulfillmentRole()));
        }
        Integer executable = outcome == null ? null : (int) outcome.getTaskOutcomes().stream()
                .filter(value -> value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.BLOCKED
                        && value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.CANCELLED
                        && value.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.FAILED)
                .count();
        return new DisplayPlanResponse(
                plan.getTasks().size(), executable, summaryLabel(plan), tasks, constraints(plan));
    }

    private static String summaryLabel(SemanticTurnPlan plan) {
        if (plan.getTasks().isEmpty()) {
            return null;
        }
        String first = plan.getTasks().get(0).getGoalLabel();
        if (plan.getTasks().size() == 1) {
            return first;
        }
        String last = plan.getTasks().get(plan.getTasks().size() - 1).getGoalLabel();
        return "从" + first + "到" + last;
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
        SemanticTurnPlan pendingPlan = Objects.requireNonNull(plan, "confirmation plan");
        return new PlanConfirmationResponse(challenge.getConfirmationId(), challenge.getExpiresAt().toString(),
                challenge.getConfirmationPlan(), challenge.getPlanFingerprint(), challenge.getIntegrityToken(),
                pendingPlan.getConfirmationPolicy().getTriggerCodes().stream()
                        .map(Enum::name).sorted().toList(),
                new PendingPlanReferenceResponse(
                        pendingPlan.getPlanId(), challenge.getPlanFingerprint()));
    }

    private ClarificationResponse toClarification(ClarificationRequest clarification) {
        List<ClarificationResponse.Field> fields = clarification.getFields().stream().map(field ->
                new ClarificationResponse.Field(field.getFieldKey(), field.getInputMode().name(),
                        field.getOptions().stream().map(option -> new ClarificationResponse.Option(
                                option.getValue(), option.getLabel(),
                                option.getSubjectType(), option.getSubjectId())).toList(),
                        field.isRequired(), field.getAffectedGoalLabels())).toList();
        List<ClarificationResponse.BlockedGoal> blockedGoals = clarification.getBlockedGoals().stream()
                .map(goal -> new ClarificationResponse.BlockedGoal(
                        goal.getGoalLabel(), goal.getReasonCode()))
                .toList();
        return new ClarificationResponse(
                clarification.getClarificationId(), clarification.getScope().name(), clarification.getPromptCode(),
                clarification.getPrompt(), fields, clarification.getBlockedTaskCount(),
                clarification.getContinuingTaskCount(), clarification.getContinuingGoalLabels(), blockedGoals);
    }

    private TaskSummaryResponse toTaskSummary(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome, boolean stpV1) {
        Map<String, SemanticTask> tasksById = indexTasks(Optional.ofNullable(plan));
        Map<String, String> indexes = plan == null ? Map.of() : displayIndexes(plan);
        List<TaskSummaryResponse.Item> items = new ArrayList<>();
        for (TaskOutcome taskOutcome : outcome.getTaskOutcomes()) {
            SemanticTask task = tasksById.get(taskOutcome.getTaskId());
            if (task != null) {
                items.add(new TaskSummaryResponse.Item(indexes.get(task.getTaskId()), task.getGoalLabel(),
                        publicTaskStatus(taskOutcome, stpV1), task.getSourceDomain(),
                        taskOutcome.getReasonCodes().stream().sorted().toList(),
                        blockedByDisplayIndexes(plan, taskOutcome, indexes),
                        stpV1 ? null : task.getFulfillmentRole()));
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

    private static List<String> blockedByDisplayIndexes(
            SemanticTurnPlan plan, TaskOutcome outcome, Map<String, String> indexes) {
        if (plan == null || outcome.getExecutionStatus() != TaskOutcome.TaskExecutionStatus.BLOCKED) {
            return List.of();
        }
        return plan.getDependencies().stream()
                .filter(dependency -> outcome.getTaskId().equals(dependency.getToTaskId()))
                .map(TaskDependency::getFromTaskId)
                .map(indexes::get)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<CompletedTaskResponse> toCompletedTasks(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome,
            ConversationAnswerResult result,
            int recommendationCount,
            Map<String, ContextHandle> contextHandles,
            boolean stpV1) {
        Map<String, SemanticTask> tasksById = indexTasks(Optional.ofNullable(plan));
        Map<String, String> indexes = plan == null ? Map.of() : displayIndexes(plan);
        List<CompletedTaskResponse> completed = new ArrayList<>();
        for (TaskOutcome outcomeItem : outcome.getTaskOutcomes()) {
            if (!outcomeItem.hasRenderablePayload()) {
                continue;
            }
            SemanticTask task = tasksById.get(outcomeItem.getTaskId());
            if (task != null) {
                CompletedTaskResponse.ResultPayload payload = outcomeItem.getResultPayload().isPresent()
                        ? toResultPayload(outcomeItem.getResultPayload().orElseThrow(), task, result,
                                recommendationCount, stpV1)
                        : new CompletedTaskResponse.ResultPayload(
                                "SECTION_RESULT",
                                toContributionBlocks(outcomeItem.getContribution().orElseThrow(), task, stpV1),
                                null, null);
                com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole publicRole =
                        stpV1 ? null : task.getFulfillmentRole();
                TaskSupportSummaryResponse publicSupport = stpV1
                        ? null : supportSummary(task, outcomeItem, result);
                completed.add(new CompletedTaskResponse(indexes.get(task.getTaskId()), task.getGoalLabel(),
                        task.getSourceDomain(), payload,
                        Optional.ofNullable(contextHandles.get(task.getTaskId()))
                                .map(ContextHandle::asBase64Url).orElse(null),
                        outcomeItem.getComposition().map(value -> new TaskCompositionResponse(
                                value.getMode(), value.isDegraded())).orElse(null),
                        publicRole, publicSupport,
                        continuationContext(task, contextHandles, stpV1)));
            }
        }
        return List.copyOf(completed);
    }

    private CompletedTaskResponse.ResultPayload toResultPayload(
            TaskResultPayload payload, SemanticTask task,
            ConversationAnswerResult result, int recommendationCount, boolean stpV1) {
        if (payload instanceof TaskResultPayload.SectionResultPayload section) {
            TaskResultProvenance provenance = provenanceFor(task, payload, result);
            return new CompletedTaskResponse.ResultPayload("SECTION_RESULT",
                    toSectionBlocks(section, task, provenance, stpV1),
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
                                    item.getEvidenceIds(),
                                    item.getSourceReferences().stream()
                                            .map(PublicSourceReferenceResponse::from).toList(),
                                    stpV1 ? null : PublicResultItemId.forRecommendation(
                                            task.getTaskId(), item.getPortfolioId()),
                                    stpV1 ? null : recommendationsPosition(recommendation, item),
                                    stpV1 ? null : subjectReference(item.getRoute(), item.getPortfolioId())))
                            .toList();
            return new CompletedTaskResponse.ResultPayload("RECOMMENDATION_RESULT",
                    toBlocks(recommendation.getSupportingBlocks(), task, provenance, stpV1),
                    recommendations, null);
        }
        TaskResultPayload.SynthesisResultPayload synthesis = (TaskResultPayload.SynthesisResultPayload) payload;
        return new CompletedTaskResponse.ResultPayload("SYNTHESIS_RESULT",
                toBlocks(synthesis.getBlocks(), task, synthesis.getProvenance(), stpV1), null,
                synthesis.getProvenance().getOriginDomains().stream().sorted().toList());
    }

    private List<ConversationAnswerBlockResponse> toBlocks(
            List<String> contents, SemanticTask task, TaskResultProvenance provenance, boolean stpV1) {
        return contents.stream().map(value -> toSupportedBlock(task, value,
                provenance.getClaimIds(), provenance.getEvidenceIds(), List.of(), provenance,
                stpV1, null, null)).filter(Objects::nonNull).toList();
    }

    private List<ConversationAnswerBlockResponse> toSectionBlocks(
            TaskResultPayload.SectionResultPayload payload,
            SemanticTask task,
            TaskResultProvenance fallbackProvenance,
            boolean stpV1) {
        return payload.getSections().stream().map(section -> {
            if (section.isTyped()) {
                return toSupportedBlock(task, section.getContent(), section.getClaimIds(),
                        section.getEvidenceIds(), section.getSourceReferences().stream()
                                .map(PublicSourceReferenceResponse::from).toList(),
                        fallbackProvenance, stpV1, section.getSectionType(), section.getTitle());
            }
            return toSupportedBlock(task, section.getContent(), fallbackProvenance.getClaimIds(),
                    fallbackProvenance.getEvidenceIds(), List.of(), fallbackProvenance, stpV1, null, null);
        }).filter(Objects::nonNull).toList();
    }

    private ConversationAnswerBlockResponse toSupportedBlock(
            SemanticTask task,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceResponse> references,
            TaskResultProvenance provenance,
            boolean stpV1,
            com.portfolio.agent.answer.domain.AnswerSectionType sectionType,
            String title) {
        ConversationSourceScope scope = sourceScope(task);
        if (stpV1) {
            return new ConversationAnswerBlockResponse(scope, sectionType, title, content,
                    claimIds, evidenceIds, references);
        }
        SemanticRoutingTypes.TaskSourceDomain domain = task.getSourceDomain();
        if (domain == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO && references.isEmpty()) {
            return null;
        }
        AnswerSupportKind kind = domain == SemanticRoutingTypes.TaskSourceDomain.GENERAL
                ? AnswerSupportKind.GENERAL_KNOWLEDGE
                : domain == SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS
                ? AnswerSupportKind.DERIVED_FROM_TASKS : AnswerSupportKind.VERIFIED_PUBLIC_EVIDENCE;
        List<String> publicKeys = references.stream()
                .map(PublicSourceReferenceResponse::getReferenceKey).toList();
        List<String> sourceTaskIds = provenance.getSourceTaskIds().isEmpty()
                ? List.of(task.getTaskId()) : provenance.getSourceTaskIds();
        List<StatementSupportReferenceResponse> statements = claimIds.stream()
                .map(id -> new StatementSupportReferenceResponse(id, sourceTaskIds.getFirst(),
                        publicKeys, resultVersion(references)))
                .toList();
        AnswerBlockSupportResponse support = new AnswerBlockSupportResponse(
                kind, statements,
                sourceTaskIds,
                publicKeys, resultVersion(references));
        return new ConversationAnswerBlockResponse(
                stableId("block", task, content, claimIds, evidenceIds), domain,
                domain == SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS ? null : scope,
                sectionType, title,
                content, claimIds, evidenceIds, references, support);
    }

    private String resultVersion(List<PublicSourceReferenceResponse> references) {
        return references.stream().map(PublicSourceReferenceResponse::getPublishedVersion)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String stableId(String prefix, String content, List<String> claimIds, List<String> evidenceIds) {
        String value = content + "|" + String.join(",", claimIds) + "|" + String.join(",", evidenceIds);
        return prefix + "-" + java.util.UUID.nameUUIDFromBytes(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String stableId(
            String prefix, SemanticTask task, String content,
            List<String> claimIds, List<String> evidenceIds) {
        return stableId(prefix, task.getTaskId() + "|" + content, claimIds, evidenceIds);
    }

    private int recommendationsPosition(
            TaskResultPayload.RecommendationResultPayload payload,
            TaskResultPayload.RecommendationItem item) {
        return payload.getItems().indexOf(item) + 1;
    }

    private SubjectReferenceResponse subjectReference(String route, String subjectId) {
        String type = route != null && route.startsWith("/cases/") ? "CASE" : "PROJECT";
        return new SubjectReferenceResponse(type, subjectId);
    }

    private ContinuationContextResponse continuationContext(
            SemanticTask task, Map<String, ContextHandle> contextHandles, boolean stpV1) {
        if (stpV1 || contextHandles == null || !contextHandles.containsKey(task.getTaskId())
                || task.getSourceDomain() != SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO) {
            return null;
        }
        return new ContinuationContextResponse(
                contextHandles.get(task.getTaskId()).asBase64Url(),
                ContextSlot.forTaskType(task.getTaskType()).contextType(), task.getTaskId());
    }

    private TaskSupportSummaryResponse supportSummary(
            SemanticTask task, TaskOutcome outcome, ConversationAnswerResult result) {
        if (task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                && !hasPublicSourceReference(outcome)) {
            return null;
        }
        TaskResultProvenance provenance = outcome.getProvenance().orElseGet(() ->
                TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()));
        AnswerSupportKind kind = task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.GENERAL
                ? AnswerSupportKind.GENERAL_KNOWLEDGE
                : task.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS
                ? AnswerSupportKind.DERIVED_FROM_TASKS : AnswerSupportKind.VERIFIED_PUBLIC_EVIDENCE;
        return new TaskSupportSummaryResponse(kind.name(),
                Math.max(provenance.getClaimIds().size(), outcome.hasRenderablePayload() ? 1 : 0),
                provenance.getEvidenceIds().size(), provenance.getSourceTaskIds().size(),
                result.getContentVersion());
    }

    private boolean hasPublicSourceReference(TaskOutcome outcome) {
        if (outcome.getContribution().isPresent()
                && !outcome.getContribution().orElseThrow().getSourceReferences().isEmpty()) {
            return true;
        }
        if (outcome.getResultPayload().isEmpty()) {
            return false;
        }
        TaskResultPayload payload = outcome.getResultPayload().orElseThrow();
        if (payload instanceof TaskResultPayload.SectionResultPayload section) {
            return section.getSections().stream()
                    .anyMatch(value -> !value.getSourceReferences().isEmpty());
        }
        if (payload instanceof TaskResultPayload.RecommendationResultPayload recommendation) {
            return recommendation.getItems().stream()
                    .anyMatch(value -> !value.getSourceReferences().isEmpty());
        }
        return false;
    }

    private AnswerSourceComposition sourceComposition(List<ConversationAnswerBlockResponse> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        java.util.Set<SemanticRoutingTypes.TaskSourceDomain> domains = new java.util.LinkedHashSet<>();
        for (ConversationAnswerBlockResponse block : blocks) {
            if (block.getSourceDomain() != null) {
                domains.add(block.getSourceDomain());
            }
        }
        if (domains.contains(SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS)) {
            return AnswerSourceComposition.CROSS_DOMAIN_DERIVED;
        }
        if (domains.contains(SemanticRoutingTypes.TaskSourceDomain.GENERAL)
                && domains.contains(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO)) {
            return AnswerSourceComposition.MULTI_SOURCE;
        }
        return domains.contains(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO)
                ? AnswerSourceComposition.PORTFOLIO_ONLY : AnswerSourceComposition.GENERAL_ONLY;
    }

    private List<PublicSourceCatalogEntryResponse> publicSourceCatalog(
            List<ConversationAnswerBlockResponse> blocks) {
        Map<String, PublicSourceCatalogEntryResponse> entries = new LinkedHashMap<>();
        for (ConversationAnswerBlockResponse block : blocks) {
            for (PublicSourceReferenceResponse reference : block.getSourceReferences()) {
                entries.putIfAbsent(reference.getReferenceKey(), new PublicSourceCatalogEntryResponse(
                        reference.getReferenceKey(), reference.getLabel(), reference.getPublishedVersion(),
                        reference.getSourceType(), reference.getSubjectRoute(), reference.getEvidenceRoute()));
            }
        }
        return List.copyOf(entries.values());
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

    private static String publicTaskStatus(TaskOutcome outcome, boolean stpV1) {
        if (stpV1) {
            return legacyTaskStatus(outcome);
        }
        if (outcome.getReasonCodes().stream().anyMatch(reason -> reason.contains("STALE"))) {
            return "STALE";
        }
        if (outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED) {
            return "PARTIAL";
        }
        if (outcome.hasRenderablePayload()) {
            return "COMPLETED";
        }
        return switch (outcome.getExecutionStatus()) {
            case REJECTED -> "REJECTED";
            case BLOCKED -> "BLOCKED";
            case FAILED -> "FAILED";
            case CANCELLED -> "NOT_EXECUTED";
            case NOT_STARTED, RUNNING, SUCCEEDED -> switch (outcome.getResolution()) {
                case NOT_SUPPORTED -> "NOT_SUPPORTED";
                case CAPABILITY_UNAVAILABLE -> "UNAVAILABLE";
                case EMPTY -> "EMPTY";
                case PRESENTATION_BLOCKED -> "BLOCKED";
                case PARTIALLY_ANSWERED -> "PARTIAL";
                case DEPENDENCY_UNAVAILABLE -> "BLOCKED";
                case NOT_EXECUTED_BUDGET -> "NOT_EXECUTED";
                case REJECTED, BOUNDARY -> "REJECTED";
                case NOT_APPLICABLE -> "NOT_APPLICABLE";
                case ANSWERED -> "FAILED";
            };
        };
    }

    private static String legacyTaskStatus(TaskOutcome outcome) {
        if (outcome.getReasonCodes().stream().anyMatch(reason -> reason.contains("STALE"))) {
            return "STALE";
        }
        if (outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED) {
            return "PARTIAL";
        }
        if (outcome.hasRenderablePayload()) {
            return "COMPLETED";
        }
        return switch (outcome.getExecutionStatus()) {
            case REJECTED -> "REJECTED";
            case BLOCKED -> "BLOCKED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case NOT_STARTED, RUNNING, SUCCEEDED -> switch (outcome.getResolution()) {
                case NOT_SUPPORTED -> "NOT_SUPPORTED";
                case CAPABILITY_UNAVAILABLE -> "UNAVAILABLE";
                case EMPTY -> "EMPTY";
                case PRESENTATION_BLOCKED -> "PRESENTATION_BLOCKED";
                case PARTIALLY_ANSWERED -> "PARTIAL";
                case DEPENDENCY_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
                case NOT_EXECUTED_BUDGET -> "NOT_EXECUTED";
                case REJECTED, BOUNDARY -> "NOT_SUPPORTED";
                case NOT_APPLICABLE -> "NOT_APPLICABLE";
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
