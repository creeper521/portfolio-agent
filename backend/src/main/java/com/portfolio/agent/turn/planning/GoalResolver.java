package com.portfolio.agent.turn.planning;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.Objects;

/**
 * 用户目标解析器：把原始 Turn 输入解释为结构化的 {@link ResolvedGoalSet}。
 *
 * <p>位于 Command → Goal 阶段。自由文本依次经过 {@link SafeConversationalFastPath}
 * （无语义社交快捷路径）、{@link UnresolvedIntentPolicy}（确定性零目标出口）
 * 与 {@link GoalInterpretationPort}（模型或 fail-closed 模板路径），语义路由再经
 * {@link SemanticRouteValidator} 校验与
 * {@link GoalBoundaryPolicy} 边界裁决；预设提问则直接由 {@link ReviewedGoalSource}
 * 从已审核快照解析。任何解释失败都收敛为 CAPABILITY_UNAVAILABLE 固定文案，
 * 绝不让未解析的目标进入 Plan 阶段。</p>
 */
public final class GoalResolver {
    private final GoalInterpretationPort interpretationPort;
    private final ReviewedGoalSource reviewedGoalSource;
    private final GoalInterpretationInputFactory inputFactory;
    private final SafeConversationalFastPath conversationalFastPath;
    private final UnresolvedIntentPolicy unresolvedIntentPolicy;
    private final SemanticRouteValidator routeValidator;
    private final GoalBoundaryPolicy boundaryPolicy;
    private final ModelOutputDiagnostics outputDiagnostics;

    /** 便捷构造器：未提供模型输出诊断时的默认无诊断实例。 */
    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            SafeConversationalFastPath conversationalFastPath,
            SemanticRouteValidator routeValidator,
            GoalBoundaryPolicy boundaryPolicy) {
        this(interpretationPort, reviewedGoalSource, inputFactory,
                conversationalFastPath, new UnresolvedIntentPolicy(),
                routeValidator, boundaryPolicy,
                ModelOutputDiagnostics.none());
    }

    /** 便捷构造器：显式注入低信息策略，使用默认无诊断实例。 */
    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            SafeConversationalFastPath conversationalFastPath,
            UnresolvedIntentPolicy unresolvedIntentPolicy,
            SemanticRouteValidator routeValidator,
            GoalBoundaryPolicy boundaryPolicy) {
        this(interpretationPort, reviewedGoalSource, inputFactory,
                conversationalFastPath, unresolvedIntentPolicy,
                routeValidator, boundaryPolicy, ModelOutputDiagnostics.none());
    }

    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            SafeConversationalFastPath conversationalFastPath,
            SemanticRouteValidator routeValidator,
            GoalBoundaryPolicy boundaryPolicy,
            ModelOutputDiagnostics outputDiagnostics) {
        this(interpretationPort, reviewedGoalSource, inputFactory,
                conversationalFastPath, new UnresolvedIntentPolicy(),
                routeValidator, boundaryPolicy, outputDiagnostics);
    }

    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            SafeConversationalFastPath conversationalFastPath,
            UnresolvedIntentPolicy unresolvedIntentPolicy,
            SemanticRouteValidator routeValidator,
            GoalBoundaryPolicy boundaryPolicy,
            ModelOutputDiagnostics outputDiagnostics) {
        this.interpretationPort = Objects.requireNonNull(
                interpretationPort, "interpretationPort");
        this.reviewedGoalSource = Objects.requireNonNull(
                reviewedGoalSource, "reviewedGoalSource");
        this.inputFactory = Objects.requireNonNull(
                inputFactory, "inputFactory");
        this.conversationalFastPath = Objects.requireNonNull(
                conversationalFastPath, "conversationalFastPath");
        this.unresolvedIntentPolicy = Objects.requireNonNull(
                unresolvedIntentPolicy, "unresolvedIntentPolicy");
        this.routeValidator = Objects.requireNonNull(
                routeValidator, "routeValidator");
        this.boundaryPolicy = Objects.requireNonNull(
                boundaryPolicy, "boundaryPolicy");
        this.outputDiagnostics = Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }

    ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline) {
        return resolve(
                command, context, deadline, ResolvedModelExecution.none());
    }

    ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        return resolve(
                command, context, deadline,
                ResolvedModelExecution.none(), recentSemanticState);
    }

    public ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        return resolve(command, context, deadline, modelExecution, null);
    }

    /**
     * 解析本轮命令为结构化目标集合。
     *
     * <p>先校验 Surface Context 的主体提示是否指向公开主体，不匹配直接返回
     * INVALID_INPUT；Ask+FreeText 走自由文本解析，其余命令交给已审核目标源。
     * 任何不可恢复的解释失败都收敛为 CAPABILITY_UNAVAILABLE，不向调用方抛出。</p>
     *
     * @param modelExecution Claim 后冻结的模型执行快照；语义结果被采纳时
     *                      标记 GOAL_INTERPRETATION 阶段
     * @param recentSemanticState 会话最近一次成功 Turn 的语义状态，可为 null
     */
    public ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(modelExecution, "modelExecution");
        if (!context.matchesHint(
                command.getSurfaceContext().getSubjectHint())) {
            return ResolvedGoalSet.invalidInput(
                    "指定的公开主体不存在或不可用。");
        }
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.FreeText) {
            return resolveFreeText(
                    ask, context, deadline, modelExecution, recentSemanticState);
        }
        try {
            return boundaryPolicy.apply(reviewedGoalSource.resolve(command));
        } catch (ReviewedGoalUnavailableException unavailable) {
            return ResolvedGoalSet.capabilityUnavailable(
                    "当前续接或澄清状态不可用，请重新提问。");
        }
    }

    /**
     * 解析自由文本 Ask：社交快捷路径 → 确定性零目标策略 → 类型化解释 → 语义路由分派。
     *
     * <p>解释不可用或语义校验失败时按剩余预算返回 CAPABILITY_UNAVAILABLE：
     * 超时与一般失败使用不同固定文案，均不暴露内部细节。</p>
     */
    private ResolvedGoalSet resolveFreeText(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        java.util.Optional<ResolvedGoalSet> conversational =
                conversationalFastPath.tryResolve(command);
        if (conversational.isPresent()) {
            return conversational.orElseThrow();
        }
        GoalInterpretationInput input = inputFactory.create(
                command, executableContext(context, modelExecution),
                recentSemanticState);
        java.util.Optional<ResolvedGoalSet> unresolved =
                unresolvedIntentPolicy.tryResolve(input);
        if (unresolved.isPresent()) {
            return unresolved.orElseThrow();
        }
        try {
            GoalInterpretationResult result =
                    interpretTyped(input, deadline, modelExecution);
            return switch (result.getKind()) {
                case SEMANTIC_ROUTE -> resolveRoute(
                        result.getRouteProposal().orElseThrow());
                case CONVERSATIONAL -> ResolvedGoalSet.providerConversational(
                        result.getMessage().orElseThrow());
            };
        } catch (GoalInterpretationUnavailableException
                 | IllegalArgumentException unavailable) {
            if (deadline.isExpired()) {
                return ResolvedGoalSet.capabilityUnavailable(
                        "目标解释已超过本轮预算，请重新提问。");
            }
            return ResolvedGoalSet.capabilityUnavailable(
                    "当前暂时无法可靠理解这条自由文本请求。");
        }
    }

    private GoalResolutionContext executableContext(
            GoalResolutionContext context,
            ResolvedModelExecution modelExecution) {
        if (modelExecution.getSnapshot().getKind() != ModelExecutionSnapshot.Kind.MODEL
                || modelExecution.getSnapshot().supports(
                        ModelCapability.GENERAL_KNOWLEDGE)) {
            return context;
        }
        java.util.EnumSet<GoalKind> allowed = context.getAllowedGoalKinds().isEmpty()
                ? java.util.EnumSet.noneOf(GoalKind.class)
                : java.util.EnumSet.copyOf(context.getAllowedGoalKinds());
        allowed.remove(GoalKind.GENERAL_EXPLANATION);
        allowed.remove(GoalKind.GENERAL_COMPARISON);
        allowed.remove(GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO);
        return new GoalResolutionContext(
                context.getPublicSubjects(), allowed,
                context.getAllowedRecommendationConstraints());
    }

    /**
     * 执行类型化目标解释并校验语义路由。
     *
     * <p>CONVERSATIONAL 结果直接采纳；SEMANTIC_ROUTE 结果先经
     * {@link SemanticRouteValidator} 做绑定与封闭校验。成功后在该
     * {@code modelExecution} 上标记 GOAL_INTERPRETATION 阶段被采纳。</p>
     *
     * @throws SelectedModelFailureException 模型执行产出的路由未通过语义校验
     * @throws IllegalArgumentException 非模型路径产出的路由未通过语义校验
     */
    public GoalInterpretationResult interpretTyped(
            GoalInterpretationInput input,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        GoalInterpretationResult result =
                interpretationPort.interpret(
                        input, deadline,
                        Objects.requireNonNull(modelExecution, "modelExecution"));
        if (result.getKind()
                == GoalInterpretationResult.Kind.CONVERSATIONAL) {
            modelExecution.markAdopted(
                    ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
            return result;
        }
        try {
            GoalInterpretationResult validated = GoalInterpretationResult.semanticRoute(
                    routeValidator.validate(
                            result.getRouteProposal().orElseThrow(), input));
            modelExecution.markAdopted(
                    ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
            return validated;
        } catch (IllegalArgumentException failure) {
            outputDiagnostics.rejected(
                    "GOAL_INTERPRETATION", ModelOutputDiagnostics.Layer.SEMANTIC);
            if (modelExecution.getSnapshot().getKind()
                    == ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException.invalidResponse(failure);
            }
            throw failure;
        }
    }

    GoalInterpretationResult interpretTyped(
            GoalInterpretationInput input,
            TurnDeadline deadline) {
        return interpretTyped(input, deadline, ResolvedModelExecution.none());
    }

    /**
     * 把语义路由提案分派为最终目标集合。
     *
     * <p>STANDARD_GOAL 再过一次 {@link GoalBoundaryPolicy} 边界裁决；
     * NEEDS_CLARIFICATION 产出澄清终态；讨论类路由（进入推荐结果、继续/
     * 切换/重入项目、开新话题）在标准解析路径缺少 typed 项目上下文，
     * 统一收敛为能力不可用。</p>
     */
    private ResolvedGoalSet resolveRoute(SemanticRouteProposal proposal) {
        return switch (proposal.getRoute()) {
            case STANDARD_GOAL -> boundaryPolicy.apply(
                    proposal.getGoalProposal().orElseThrow());
            case NEEDS_CLARIFICATION -> ResolvedGoalSet.clarification(
                    proposal.getClarification().orElseThrow());
            case ENTER_RECOMMENDED_RESULT,
                    CONTINUE_CURRENT_PROJECT,
                    START_NEW_TOPIC,
                    SWITCH_PROJECT,
                    REENTER_PROJECT -> ResolvedGoalSet.capabilityUnavailable(
                    "当前语义路由需要有效的 typed 项目上下文。");
        };
    }
}
