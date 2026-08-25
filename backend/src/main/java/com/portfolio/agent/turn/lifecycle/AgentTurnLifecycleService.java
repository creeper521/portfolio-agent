package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.infrastructure.model.ModelExecutionResolutionException;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
import com.portfolio.agent.turn.continuation.ClarificationAnswerNormalizer;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ClarificationSettlementMutation;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.continuation.ConversationSemanticStateProjector;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ContinuationReference;
import com.portfolio.agent.turn.continuation.ActiveDiscussionPointer;
import com.portfolio.agent.turn.continuation.DiscussionStateMutation;
import com.portfolio.agent.turn.continuation.ProjectDiscussionContext;
import com.portfolio.agent.turn.continuation.ProjectDiscussionCoordinator;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.DiscussionSelectionTemplate;
import com.portfolio.agent.turn.planning.DiscussionClarificationTemplate;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalResolutionContext;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.PlanCompilationResult;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.SemanticRouteProposal;
import com.portfolio.agent.turn.planning.ValidatedSemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.projection.ModelExecutionProjection;
import com.portfolio.agent.turn.projection.ModelExecutionProjectionFactory;
import com.portfolio.agent.turn.projection.SuggestedAction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Agent 轮次生命周期编排服务：Command → Goal → Plan → Execution → PublicAgentTurn
 * → Settlement 的唯一运行时权威。
 *
 * <p>串行编排 会话解析 → 幂等 Claim（含重放）→ 模型选择解析 → 目标解析 → 计划编译 →
 * 语义执行 → 公众投影 → 单次原子结算，并持有活跃轮次取消注册表与 State 执行线程。
 * 任何阶段失败都收敛为终止态 PublicAgentTurn 或 STORE_UNAVAILABLE，不留半完成状态；
 * State 读写全部经由 stateExecutor 并受 TurnDeadline 与 settlementReserve 约束。</p>
 */
public final class AgentTurnLifecycleService {
    private final ConversationSemanticStateProjector semanticStateProjector =
            new ConversationSemanticStateProjector();
    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final GoalResolver goalResolver;
    private final SemanticPlanCompiler planCompiler;
    private final SemanticTurnEngine engine;
    private final PublicAgentTurnProjector projector;
    private final ContextMutationPlanner mutationPlanner;
    private final AgentStateStore store;
    private final RequestFingerprintFactory fingerprintFactory;
    private final ConversationSessionResolver sessionResolver;
    private final ExecutorService stateExecutor;
    private final PersistenceSafeReplayPolicy replayPolicy =
            new PersistenceSafeReplayPolicy();
    private final ActiveTurnRegistry activeTurns = new ActiveTurnRegistry();
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration turnTimeout;
    private final Duration settlementReserve;
    private final Duration contextTtl;
    private final ProjectDiscussionCoordinator discussionCoordinator;
    private final ModelExecutionResolver modelExecutionResolver;
    private final ModelExecutionProjectionFactory modelExecutionProjectionFactory =
            new ModelExecutionProjectionFactory();

    public AgentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler, SemanticTurnEngine engine,
            PublicAgentTurnProjector projector, ContextMutationPlanner mutationPlanner,
            AgentStateStore store, RequestFingerprintFactory fingerprintFactory,
            ConversationSessionResolver sessionResolver,
            ExecutorService stateExecutor,
            Clock clock, Duration leaseDuration,
            Duration turnTimeout, Duration settlementReserve,
            Duration contextTtl,
            ModelExecutionResolver modelExecutionResolver) {
        this.knowledgeGateway = java.util.Objects.requireNonNull(knowledgeGateway);
        this.goalResolver = java.util.Objects.requireNonNull(goalResolver);
        this.planCompiler = java.util.Objects.requireNonNull(planCompiler);
        this.engine = java.util.Objects.requireNonNull(engine);
        this.projector = java.util.Objects.requireNonNull(projector);
        this.mutationPlanner = java.util.Objects.requireNonNull(mutationPlanner);
        this.store = java.util.Objects.requireNonNull(store);
        this.fingerprintFactory = java.util.Objects.requireNonNull(fingerprintFactory);
        this.sessionResolver = java.util.Objects.requireNonNull(sessionResolver);
        this.stateExecutor = java.util.Objects.requireNonNull(stateExecutor);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.turnTimeout = positive(turnTimeout, "turnTimeout");
        this.settlementReserve = positive(settlementReserve, "settlementReserve");
        if (settlementReserve.compareTo(turnTimeout) >= 0) {
            throw new IllegalArgumentException("settlementReserve must be shorter than turnTimeout");
        }
        this.contextTtl = positive(contextTtl, "contextTtl");
        this.modelExecutionResolver = java.util.Objects.requireNonNull(
                modelExecutionResolver, "modelExecutionResolver");
        this.discussionCoordinator = new ProjectDiscussionCoordinator(
                () -> "discussion_"
                        + UUID.randomUUID().toString().replace("-", ""),
                clock, contextTtl.compareTo(Duration.ofMinutes(30)) > 0
                ? Duration.ofMinutes(30) : contextTtl);
    }

    AgentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler, SemanticTurnEngine engine,
            PublicAgentTurnProjector projector, ContextMutationPlanner mutationPlanner,
            AgentStateStore store, RequestFingerprintFactory fingerprintFactory,
            ConversationSessionResolver sessionResolver,
            ExecutorService stateExecutor,
            Clock clock, Duration leaseDuration,
            Duration turnTimeout, Duration settlementReserve,
            Duration contextTtl) {
        this(knowledgeGateway, goalResolver, planCompiler, engine,
                projector, mutationPlanner, store, fingerprintFactory,
                sessionResolver, stateExecutor, clock, leaseDuration,
                turnTimeout, settlementReserve, contextTtl,
                new ModelExecutionResolver(
                        com.portfolio.agent.infrastructure.model.provider
                                .ModelCatalogSnapshot.empty(),
                        modelRef -> {
                            throw new IllegalArgumentException(
                                    "model binding is unavailable in deterministic test lifecycle");
                        }));
    }

    /**
     * 执行一个 Turn：解析会话凭证后在租期内完成 Claim → 执行 → 结算。
     *
     * <p>匿名首次请求会签发试探性会话，仅在结算成功（或重放）时才提交会话并回传
     * ResumeToken 与讨论摘要；UNAUTHORIZED、凭据无效等情形不回传任何会话信息。</p>
     *
     * @param bearerToken ResumeToken 凭证字面值，可为 null（匿名）
     * @return 含状态、Turn、会话元数据的 {@link Result}；不抛出业务异常
     */
    public Result execute(String bearerToken, AgentTurnCommand command) {
        Instant turnStartedAt = clock.instant();
        TurnDeadline turnDeadline = new TurnDeadline(
                turnStartedAt.plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, command.getRequestId(), turnDeadline);
        if (session.status() == ConversationSessionResolver.Status.INVALID) {
            return Result.state(Status.UNAUTHORIZED, 0);
        }
        ConversationSessionStore.Session pendingSession =
                sessionResolver.pendingSession(session);
        ConversationSessionStore.Session sessionAuthority =
                session.session() == null ? pendingSession : session.session();
        Result result = executeResolved(
                session.conversationId(), session.tokenHash(),
                pendingSession, sessionAuthority, command,
                turnStartedAt, turnDeadline);
        boolean canCommitSession = (result.status() == Status.COMPLETED
                || result.status() == Status.REPLAY) && !result.settlementFailed();
        if (canCommitSession || session.status() == ConversationSessionResolver.Status.AUTHENTICATED) {
            SessionProjection projection = result.sessionProjection() != null
                    ? result.sessionProjection()
                    : sessionProjection(sessionAuthority);
            return result.withConversation(new ConversationMetadata(
                    session.conversationId(), session.issuedToken() == null
                    ? null : session.issuedToken().encode(),
                    projection == null ? 0 : projection.revision(),
                    projection == null || projection.pointer() == null
                            ? null : discussionSummary(
                            projection.pointer(), knowledgeGateway.getContent())));
        }
        return result;
    }

    /**
     * Claim 之后的主执行路径：在 stateExecutor 上限时执行 Claim，按结果分流
     * （重放/进行中/冲突/取消/认领），认领成功时注册取消动作并执行语义管线，
     * 最后统一结算。State 超时、中断或异常一律收敛为 STORE_UNAVAILABLE，
     * 模型解析与执行失败收敛为可结算的 CapabilityUnavailable Turn。
     */
    private Result executeResolved(
            String conversationId, byte[] resumeTokenHash,
            ConversationSessionStore.Session sessionToCreate,
            ConversationSessionStore.Session sessionAuthority,
            AgentTurnCommand command, Instant turnStartedAt,
            TurnDeadline turnDeadline) {
        RequestFingerprintSet fingerprints = fingerprintFactory.fingerprints(command);
        byte[] fingerprint = fingerprints.current();
        TurnExecutionStore.SessionAccess sessionAccess = sessionToCreate == null
                ? TurnExecutionStore.SessionAccess.authenticated(
                conversationId, resumeTokenHash)
                : TurnExecutionStore.SessionAccess.tentative(sessionToCreate);
        TurnExecutionStore.ClaimResult claim;
        Future<TurnExecutionStore.ClaimResult> claimTask = stateExecutor.submit(
                () -> store.claim(
                        command.getRequestId(), conversationId, fingerprints, sessionAccess,
                        turnStartedAt, leaseDuration, turnDeadline));
        try {
            long remainingMillis = turnDeadline.remainingMillis();
            if (remainingMillis < 1) {
                claimTask.cancel(true);
                return Result.state(Status.STORE_UNAVAILABLE, 0);
            }
            claim = claimTask.get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            claimTask.cancel(true);
            return Result.state(Status.STORE_UNAVAILABLE, 0);
        } catch (InterruptedException interrupted) {
            claimTask.cancel(true);
            Thread.currentThread().interrupt();
            return Result.state(Status.STORE_UNAVAILABLE, 0);
        } catch (ExecutionException failure) {
            return Result.state(Status.STORE_UNAVAILABLE, 0);
        }
        if (turnDeadline.isExpired()) {
            return Result.state(Status.STORE_UNAVAILABLE, 0);
        }
        switch (claim.status()) {
            case REPLAY: return new Result(
                    Status.REPLAY, claim.replay(), 0, false, null,
                    sessionProjection(claim.sessionSnapshot() == null
                            ? sessionAuthority : claim.sessionSnapshot()));
            case IN_PROGRESS: return Result.state(Status.IN_PROGRESS, claim.retryAfterSeconds());
            case CONFLICT: return Result.state(Status.CONFLICT, 0);
            case CANCELLED: return Result.state(Status.CANCELLED, 0);
            case CLAIMED: break;
        }
        ResolvedModelExecution modelExecution;
        try {
            modelExecution = modelExecutionResolver.resolve(
                    command.getModelSelection());
        } catch (ModelExecutionResolutionException failure) {
            Execution unavailable = modelSelectionUnavailable(
                    command.getRequestId(), failure.getCode());
            unavailable = withModelExecution(
                    unavailable,
                    modelExecutionProjectionFactory.selectionOnly(
                            command.getModelSelection()));
            return settle(
                    command.getRequestId(), fingerprint,
                    unavailable, sessionToCreate, sessionAuthority,
                    sessionAccess, turnDeadline);
        }
        CancellationSignal cancellation = new CancellationSignal();
        Runnable cancelAction = cancellation::cancel;
        activeTurns.claimOwner(command.getRequestId(), cancelAction);
        try {
            Execution execution = executeClaimed(
                    conversationId, resumeTokenHash, sessionAuthority,
                    command, cancellation, modelExecution,
                    turnDeadline);
            execution = withModelExecution(
                    execution,
                    modelExecutionProjectionFactory.project(modelExecution));
            return settle(
                    command.getRequestId(), fingerprint,
                    execution, sessionToCreate, sessionAuthority,
                    sessionAccess, turnDeadline);
        } catch (SelectedModelFailureException failure) {
            Execution unavailable = selectedModelUnavailable(
                    command.getRequestId(), failure);
            unavailable = withModelExecution(
                    unavailable,
                    modelExecutionProjectionFactory.project(modelExecution));
            return settle(
                    command.getRequestId(), fingerprint,
                    unavailable, sessionToCreate, sessionAuthority,
                    sessionAccess, turnDeadline);
        } finally {
            activeTurns.releaseOwner(command.getRequestId(), cancelAction);
        }
    }

    /**
     * 单次原子结算：把执行结果（快照、上下文、challenge、会话、讨论/澄清/语义状态
     * 变更）交给 Store 的 completeWithSession，剩余预算不足或 Store 失败时标记
     * settlementFailed 并降级为只读返回。结算成功才返回 COMPLETED。
     */
    private Result settle(
            UUID requestId, byte[] fingerprint,
            Execution execution, ConversationSessionStore.Session sessionToCreate,
            ConversationSessionStore.Session sessionAuthority,
            TurnExecutionStore.SessionAccess sessionAccess,
            TurnDeadline turnDeadline) {
        long remainingMillis = turnDeadline.remainingMillis();
        if (remainingMillis < 1) {
            return settlementFailed(execution, sessionAuthority);
        }
        Future<TurnExecutionStore.SettlementResult> settlement =
                stateExecutor.submit(() -> execution.semanticState() == null
                        ? store.completeWithSession(
                        requestId, fingerprint, execution.replayTurn(),
                        execution.contexts(), execution.challenges(),
                        sessionToCreate, sessionAccess, clock.instant(),
                        turnDeadline, execution.discussionMutation(),
                        execution.clarificationMutation())
                        : store.completeWithSession(
                        requestId, fingerprint, execution.replayTurn(),
                        execution.contexts(), execution.challenges(),
                        sessionToCreate, sessionAccess, clock.instant(),
                        turnDeadline, execution.discussionMutation(),
                        execution.clarificationMutation(),
                        execution.semanticState()));
        try {
            TurnExecutionStore.SettlementResult completed =
                    settlement.get(remainingMillis, TimeUnit.MILLISECONDS);
            if (!completed.completed()) {
                return Result.state(Status.CANCELLED, 0);
            }
            return new Result(
                    Status.COMPLETED, execution.settledTurn(), 0,
                    false, null,
                    sessionProjection(completed.sessionSnapshot()));
        } catch (TimeoutException timeout) {
            settlement.cancel(true);
            return settlementFailed(execution, sessionAuthority);
        } catch (InterruptedException interrupted) {
            settlement.cancel(true);
            Result result = settlementFailed(execution, sessionAuthority);
            Thread.currentThread().interrupt();
            return result;
        } catch (ExecutionException failure) {
            return settlementFailed(execution, sessionAuthority);
        }
    }

    private Result settlementFailed(
            Execution execution,
            ConversationSessionStore.Session sessionAuthority) {
        return new Result(
                Status.COMPLETED, execution.readOnlyTurn(), 0, true, null,
                sessionProjection(sessionAuthority));
    }

    private SessionProjection sessionProjection(
            ConversationSessionStore.Session session) {
        return session == null ? null : new SessionProjection(
                session.activeDiscussion().orElse(null),
                session.discussionRevision());
    }

    /**
     * 按凭证取消一个 Turn：先触发进程内取消信号，再在 State 层做 CLAIMED →
     * CANCELLED 迁移，并按已存记录的终态返回相应取消结果。
     *
     * @return 取消是否生效，或 ALREADY_COMPLETED / NOT_FOUND / UNAUTHORIZED / STORE_UNAVAILABLE
     */
    public CancelStatus cancel(String bearerToken, UUID requestId) {
        TurnDeadline deadline = new TurnDeadline(
                clock.instant().plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, requestId, deadline);
        if (session.status() == ConversationSessionResolver.Status.INVALID) return CancelStatus.UNAUTHORIZED;
        activeTurns.cancel(requestId);
        try {
            if (store.cancel(requestId, session.conversationId(), clock.instant())) {
                return CancelStatus.CANCELLED;
            }
            return store.find(requestId).map(value ->
                    value.getStatus() == TurnExecutionRecord.Status.COMPLETED
                            ? CancelStatus.ALREADY_COMPLETED
                            : value.getStatus() == TurnExecutionRecord.Status.CANCELLED
                            ? CancelStatus.CANCELLED : CancelStatus.NOT_FOUND)
                    .orElse(CancelStatus.NOT_FOUND);
        } catch (RuntimeException failure) {
            return CancelStatus.STORE_UNAVAILABLE;
        }
    }

    /** 查询当前凭证对应的匿名会话状态：会话 ID、讨论修订号与活跃讨论摘要。 */
    public ConversationStatus currentConversation(String bearerToken) {
        TurnDeadline deadline = new TurnDeadline(
                clock.instant().plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID(), deadline);
        if (session.status()
                != ConversationSessionResolver.Status.AUTHENTICATED) {
            return new ConversationStatus(false, null, null);
        }
        DiscussionSummary discussion = session.session()
                .activeDiscussion().map(pointer ->
                        discussionSummary(pointer, knowledgeGateway.getContent()))
                .orElse(null);
        return new ConversationStatus(
                true, session.conversationId(),
                session.session().discussionRevision(), discussion);
    }

    private DiscussionSummary discussionSummary(
            ActiveDiscussionPointer pointer,
            RuntimeAnswerContent content) {
        AnswerKnowledge project = content.getProjects().stream()
                .filter(value -> value.getStableId()
                        .equals(pointer.getProjectId()))
                .findFirst().orElse(null);
        return new DiscussionSummary(
                pointer.statusAt(clock.instant()),
                pointer.getProjectId(),
                project == null ? "项目已不可用" : project.getTitle(),
                project == null ? "/projects"
                        : "/projects/" + project.getSlug(),
                pointer.getContextExpiresAt(),
                pointer.getContextHandle());
    }

    /** 清空当前匿名会话（吊销凭证并删除会话状态）；仅对存活会话返回 true。 */
    public boolean clearConversation(String bearerToken) {
        TurnDeadline deadline = new TurnDeadline(
                clock.instant().plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID(), deadline);
        if (session.status() != ConversationSessionResolver.Status.AUTHENTICATED) return false;
        return store.clearConversation(
                session.conversationId(), session.tokenHash(), clock.instant());
    }

    /**
     * 已认领 Turn 的语义分发：先处理讨论续跑（进入/重进/退出/上下文内追问），
     * 再处理携带 referenceContextHandle 的推荐上下文路由，最后走通用输入解析
     * （澄清回答预留、目标解析）并按 Goal 结果类别分发执行。
     * 全部路径最终产出一个可结算的 {@link Execution}。
     */
    private Execution executeClaimed(
            String conversationId, byte[] resumeTokenHash,
            ConversationSessionStore.Session sessionAuthority,
            AgentTurnCommand command, CancellationSignal cancellation,
            ResolvedModelExecution modelExecution,
            TurnDeadline turnDeadline) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        if (command instanceof AgentTurnCommand.Continue continuation) {
            if (continuation.getOperation()
                    == AgentTurnCommand.ContinueOperation.ENTER_RESULT) {
                return enterDiscussion(
                        conversationId, sessionAuthority,
                        continuation, cancellation, content, turnDeadline,
                        modelExecution);
            }
            if (continuation.getOperation()
                    == AgentTurnCommand.ContinueOperation.REENTER_SUBJECT) {
                return reenterDiscussion(
                        conversationId, sessionAuthority,
                        continuation, cancellation, content, turnDeadline,
                        modelExecution);
            }
            if (continuation.getOperation()
                    == AgentTurnCommand.ContinueOperation.EXIT_CONTEXT) {
                return exitDiscussion(
                        sessionAuthority, continuation);
            }
            if (continuation.getOperation()
                    == AgentTurnCommand.ContinueOperation.ROUTE_IN_CONTEXT) {
                return routeDiscussion(
                        conversationId, sessionAuthority,
                        command, continuation.getText().orElseThrow(),
                        cancellation, content, turnDeadline, modelExecution);
            }
        }
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.FreeText freeText
                && sessionAuthority != null
                && sessionAuthority.activeDiscussion().isPresent()) {
            return routeDiscussion(
                    conversationId, sessionAuthority,
                    command, freeText.getText(),
                    cancellation, content, turnDeadline, modelExecution);
        }
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.FreeText freeText
                && ask.getReferenceContextHandle().isPresent()) {
            Execution referenced = routeRecommendationReference(
                    conversationId, resumeTokenHash, sessionAuthority,
                    command, freeText.getText(),
                    ask.getReferenceContextHandle().orElseThrow(),
                    cancellation, content, turnDeadline, modelExecution);
            if (referenced != null) return referenced;
        }
        ResolvedInput input = resolveInput(
                conversationId, resumeTokenHash, sessionAuthority,
                command, content,
                turnDeadline, modelExecution);
        if (input.discussionClarification() != null) {
            return withClarificationMutation(
                    executeDiscussionClarification(
                            conversationId, sessionAuthority, command,
                            cancellation, content, turnDeadline,
                            input.discussionClarification(), modelExecution),
                    input.clarificationMutation());
        }
        if (input.discussionSelection() != null) {
            DiscussionSelectionResolution selection = input.discussionSelection();
            AgentTurnCommand.Continue enter = new AgentTurnCommand.Continue(
                    command.getRequestId(), command.getModelSelection(),
                    AgentTurnCommand.ContinueOperation.ENTER_RESULT,
                    selection.contextHandle(), selection.resultItemId(), null, null,
                    command.getSurfaceContext(), command.getConversationWindow());
            return withClarificationMutation(enterDiscussion(
                    conversationId, sessionAuthority, enter,
                    cancellation, content, turnDeadline, modelExecution),
                    input.clarificationMutation());
        }
        ResolvedGoalSet resolved = input.resolved();
        Execution execution = switch (resolved.getKind()) {
            case CONVERSATIONAL -> {
                PublicAgentTurn turn = new PublicAgentTurn.Conversational(
                        command.getRequestId(), resolved.getMessage().orElseThrow(), List.of());
                yield switch (resolved.getMessageSource()) {
                    case SERVER_FIXED -> serverFixed(turn);
                    case PROVIDER_DERIVED, NONE -> providerBody(turn);
                };
            }
            case BOUNDARY, INVALID_INPUT -> serverFixed(new PublicAgentTurn.Boundary(
                    command.getRequestId(), resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                    ? "PUBLIC_SUBJECT_INVALID" : "OUT_OF_SCOPE",
                    resolved.getMessage().orElseThrow(),
                    resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                            ? List.of(new SuggestedAction(
                            "ask-new-question", "重新提问", "请重新描述你的问题", null))
                            : List.of()));
            case CAPABILITY_UNAVAILABLE -> serverFixed(new PublicAgentTurn.CapabilityUnavailable(
                    command.getRequestId(), input.capabilityCode() != null
                    ? input.capabilityCode()
                    : command instanceof AgentTurnCommand.Ask
                    ? "SEMANTIC_ROUTING_UNAVAILABLE" : "CONTINUATION_UNAVAILABLE",
                    resolved.getMessage().orElseThrow(),
                    input.capabilityCode() != null
                            || command instanceof AgentTurnCommand.Ask,
                    input.retryAfterSeconds(), List.of()));
            case CLARIFICATION -> clarification(
                    conversationId, resumeTokenHash, command.getRequestId(),
                    content, resolved.getClarification().orElseThrow());
            case GOALS -> goals(
                    conversationId, resumeTokenHash, command, cancellation, content,
                    planCompiler.compile(
                            resolved.getGoalProposal().orElseThrow(), content.getContentVersion(),
                            resolutionContext(content), audience(command)),
                    turnDeadline.minus(settlementReserve), modelExecution);
        };
        return withClarificationMutation(
                execution, input.clarificationMutation());
    }

    /**
     * 携带 referenceContextHandle 的自由文本路由：加载推荐 ContinuationContext，
     * 用候选项目构造受限 Goal 解析输入，按解析出的 Route 分发——标准目标、直接
     * 进入推荐结果或发起推荐选择澄清。上下文缺失、过期或内容版本不匹配时返回
     * null 交回通用路径。
     */
    private Execution routeRecommendationReference(
            String conversationId,
            byte[] tokenHash,
            ConversationSessionStore.Session session,
            AgentTurnCommand command,
            String text,
            String referenceContextHandle,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (session == null) return null;
        ContinuationContext loaded;
        try {
            loaded = store.findContext(
                    conversationId, referenceContextHandle,
                    clock.instant(),
                    deadline.minus(settlementReserve)).orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
        if (!(loaded instanceof ContinuationContext.Recommendation recommendation)
                || !recommendation.getContentReleaseId()
                .equals(content.getContentVersion())) {
            return null;
        }
        List<GoalInterpretationInput.RouteCandidate> candidates =
                recommendationRouteCandidates(recommendation, content);
        if (candidates.size()
                != recommendation.getSelectedResults().size()) {
            return null;
        }
        GoalResolutionContext goalContext = resolutionContext(content);
        GoalInterpretationInput input = new GoalInterpretationInput(
                text,
                command.getConversationWindow().getMessages().stream()
                        .map(message -> message.getRole().name()
                                + ":" + message.getText())
                        .toList(),
                goalContext.getPublicSubjects(),
                Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, candidates,
                Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.ENTER_RECOMMENDED_RESULT,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                goalContext.getAllowedRecommendationConstraints(),
                goalContext.resolveHint(command.getSurfaceContext().getSubjectHint()),
                audience(command));
        GoalInterpretationResult interpretation;
        try {
            interpretation = goalResolver.interpretTyped(
                    input, deadline.minus(settlementReserve), modelExecution);
        } catch (GoalInterpretationUnavailableException
                 | IllegalArgumentException unavailable) {
            return serverFixed(new PublicAgentTurn.CapabilityUnavailable(
                    command.getRequestId(),
                    "SEMANTIC_ROUTING_UNAVAILABLE",
                    "当前暂时无法可靠理解这条自由文本请求。",
                    true, List.of()));
        }
        if (interpretation.getKind()
                == GoalInterpretationResult.Kind.CONVERSATIONAL) {
            return providerBody(new PublicAgentTurn.Conversational(
                    command.getRequestId(),
                    interpretation.getMessage().orElseThrow(),
                    List.of()));
        }
        SemanticRouteProposal proposal =
                interpretation.getRouteProposal().orElseThrow();
        return switch (proposal.getRoute()) {
            case STANDARD_GOAL -> goals(
                    conversationId, tokenHash,
                    command, cancellation, content,
                    planCompiler.compile(
                            proposal.getGoalProposal().orElseThrow(),
                            content.getContentVersion(),
                            resolutionContext(content), audience(command)),
                    deadline.minus(settlementReserve), modelExecution);
            case ENTER_RECOMMENDED_RESULT -> {
                String candidateKey =
                        proposal.getCandidateKey().orElseThrow();
                yield enterRecommendationCandidate(
                        conversationId, session, command, cancellation,
                        content, deadline, recommendation, candidates,
                        candidateKey, modelExecution);
            }
            case NEEDS_CLARIFICATION ->
                    recommendationSelectionClarification(
                            conversationId, tokenHash,
                            command.getRequestId(),
                            content, recommendation, candidates);
            default -> discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_INTERPRETATION_UNAVAILABLE");
        };
    }

    /** 解析候选键到项目后经讨论协调器进入该项目结果，并执行讨论状态迁移。 */
    private Execution enterRecommendationCandidate(
            String conversationId,
            ConversationSessionStore.Session session,
            AgentTurnCommand command,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            ContinuationContext.Recommendation recommendation,
            List<GoalInterpretationInput.RouteCandidate> candidates,
            String candidateKey,
            ResolvedModelExecution modelExecution) {
        String projectId = candidateProjectId(candidateKey, candidates);
        String resultItemId = recommendation.getSelectedResults().stream()
                .filter(item -> item.subjectId().equals(projectId))
                .map(ContinuationContext.ResultItem::resultItemId)
                .findFirst().orElseThrow();
        ProjectDiscussionCoordinator.Transition transition =
                discussionCoordinator.enter(
                        conversationId,
                        content.getContentVersion(),
                        recommendation,
                        resultItemId,
                        publicProjectIds(content),
                        session.expiresAt());
        return executeDiscussionTransition(
                command, cancellation, content, deadline,
                null, transition, modelExecution);
    }

    /** 把推荐结果映射为受限路由候选（只保留仍存在于公开内容中的项目）。 */
    private List<GoalInterpretationInput.RouteCandidate>
            recommendationRouteCandidates(
            ContinuationContext.Recommendation recommendation,
            RuntimeAnswerContent content) {
        List<GoalInterpretationInput.RouteCandidate> candidates =
                new ArrayList<>();
        for (int index = 0;
                index < recommendation.getSelectedResults().size();
                index++) {
            ContinuationContext.ResultItem item =
                    recommendation.getSelectedResults().get(index);
            AnswerKnowledge project = content.getProjects().stream()
                    .filter(value -> value.getStableId()
                            .equals(item.subjectId()))
                    .findFirst().orElse(null);
            if (project == null) continue;
            candidates.add(new GoalInterpretationInput.RouteCandidate(
                    "C" + (index + 1),
                    GoalSubjectReference.Kind.PROJECT,
                    project.getStableId(),
                    project.getTitle(),
                    Set.of(
                            project.getStableId(),
                            project.getSlug(),
                            project.getTitle())));
        }
        return List.copyOf(candidates);
    }

    /** 构造"选择要讨论的推荐项目"澄清：选项绑定到 result-item，供后续typed选择。 */
    private Execution recommendationSelectionClarification(
            String conversationId,
            byte[] tokenHash,
            UUID requestId,
            RuntimeAnswerContent content,
            ContinuationContext.Recommendation recommendation,
            List<GoalInterpretationInput.RouteCandidate> candidates) {
        String clarificationId =
                "clarification_"
                        + requestId.toString().replace("-", "");
        List<ClarificationChallenge.Choice> choices =
                new ArrayList<>();
        Map<String, String> bindings =
                new LinkedHashMap<>();
        Set<String> allowedItems =
                new java.util.LinkedHashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            GoalInterpretationInput.RouteCandidate candidate =
                    candidates.get(index);
            ContinuationContext.ResultItem item =
                    recommendation.getSelectedResults().get(index);
            String choiceId = "choice_result_" + (index + 1);
            choices.add(new ClarificationChallenge.Choice(
                    choiceId, candidate.getLabel()));
            bindings.put(
                    choiceId, "result-item:" + item.resultItemId());
            allowedItems.add(item.resultItemId());
        }
        ChallengeDefinition definition = choiceChallenge(
                clarificationId,
                "请选择要继续讨论的推荐项目。",
                "field_recommendation_result",
                "推荐项目", choices, bindings);
        DiscussionSelectionTemplate template =
                new DiscussionSelectionTemplate(
                        recommendation.getContextHandle(),
                        Set.copyOf(allowedItems));
        ClarificationStore.Record record =
                new ClarificationStore.Record(
                        conversationId, tokenHash,
                        content.getContentVersion(),
                        definition.challenge(),
                        definition.choiceBindings(),
                        definition.textBindings(),
                        template);
        PublicAgentTurn turn = new PublicAgentTurn.Clarification(
                requestId,
                "需要确认要讨论的推荐项目。",
                definition.challenge(), List.of());
        return new Execution(
                turn, turn, turn, List.of(), List.of(record),
                DiscussionStateMutation.none());
    }

    /**
     * 项目讨论内的路由：以会话活跃讨论指针为锁，按指针状态（ACTIVE/EXPIRED）给
     * 模型不同的受限路由集合，解析后分发——继续当前项目、开新话题、切换项目、
     * 重进项目或发起讨论方向澄清。解析不可用时返回带 guard 变更的降级 Turn，
     * 防止过期指针被意外清除。
     */
    private Execution routeDiscussion(
            String conversationId,
            ConversationSessionStore.Session session,
            AgentTurnCommand command,
            String text,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        ActiveDiscussionPointer pointer =
                session.activeDiscussion().orElse(null);
        if (pointer == null) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        if (command instanceof AgentTurnCommand.Continue continuation
                && !pointer.matchesGeneration(
                continuation.getContextHandle().orElseThrow())) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_MISMATCH");
        }
        GoalInterpretationInput.PublicSubjectDescriptor locked =
                resolutionContext(content).getPublicSubjects().stream()
                .filter(subject ->
                        subject.getKind()
                        == GoalSubjectReference.Kind.PROJECT
                        && subject.getReference()
                        .equals(pointer.getProjectId()))
                .findFirst().orElse(null);
        if (locked == null) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_SUBJECT_UNAVAILABLE");
        }
        ActiveDiscussionPointer.Status pointerStatus =
                pointer.statusAt(clock.instant());
        ProjectDiscussionContext context = null;
        if (pointerStatus == ActiveDiscussionPointer.Status.ACTIVE) {
            ContinuationContext loaded = store.findContext(
                    conversationId, pointer.getContextHandle(),
                    clock.instant(),
                    deadline.minus(settlementReserve)).orElse(null);
            if (!(loaded instanceof ProjectDiscussionContext discussion)) {
                return discussionUnavailable(
                        command.getRequestId(),
                        "DISCUSSION_CONTEXT_UNAVAILABLE");
            }
            context = discussion;
        }
        List<GoalInterpretationInput.RouteCandidate> candidates =
                context == null ? List.of()
                        : routeCandidates(context, content);
        Set<SemanticRouteProposal.Route> routes =
                pointerStatus == ActiveDiscussionPointer.Status.ACTIVE
                        ? Set.of(
                        SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT,
                        SemanticRouteProposal.Route.START_NEW_TOPIC,
                        SemanticRouteProposal.Route.SWITCH_PROJECT,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION)
                        : Set.of(
                        SemanticRouteProposal.Route.REENTER_PROJECT,
                        SemanticRouteProposal.Route.START_NEW_TOPIC,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION);
        GoalInterpretationInput input =
                new GoalInterpretationInput(
                        text,
                        command.getConversationWindow().getMessages().stream()
                                .map(message -> message.getRole().name()
                                        + ":" + message.getText())
                                .toList(),
                        resolutionContext(content).getPublicSubjects(),
                        Set.of(
                                GoalKind.PORTFOLIO_FACT,
                                GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO),
                        GoalInterpretationInput.InterpretationMode.DISCUSSION,
                        pointerStatus == ActiveDiscussionPointer.Status.ACTIVE
                                ? GoalInterpretationInput.DiscussionState.ACTIVE
                                : GoalInterpretationInput.DiscussionState.EXPIRED,
                        locked, candidates, routes,
                        resolutionContext(content).getAllowedRecommendationConstraints(),
                        null, audience(command));
        GoalInterpretationResult interpretation;
        try {
            interpretation = goalResolver.interpretTyped(
                    input, deadline.minus(settlementReserve), modelExecution);
        } catch (GoalInterpretationUnavailableException
                 | IllegalArgumentException unavailable) {
            return withMutation(
                    discussionInterpretationUnavailable(
                            command.getRequestId(), pointer),
                    DiscussionStateMutation.guard(
                            pointer.getContextHandle()));
        }
        if (interpretation.getKind()
                == GoalInterpretationResult.Kind.CONVERSATIONAL) {
            return withMutation(
                    providerBody(new PublicAgentTurn.Conversational(
                            command.getRequestId(),
                            interpretation.getMessage().orElseThrow(),
                            List.of())),
                    DiscussionStateMutation.guard(
                            pointer.getContextHandle()));
        }
        SemanticRouteProposal proposal =
                interpretation.getRouteProposal().orElseThrow();
        return switch (proposal.getRoute()) {
            case CONTINUE_CURRENT_PROJECT -> {
                Execution execution = goals(
                        conversationId, new byte[0],
                        command, cancellation, content,
                        planCompiler.compile(
                                proposal.getGoalProposal().orElseThrow(),
                                content.getContentVersion(),
                                resolutionContext(content), audience(command)),
                        deadline.minus(settlementReserve), modelExecution);
                yield withMutation(
                        execution,
                        DiscussionStateMutation.guard(
                                pointer.getContextHandle()));
            }
            case START_NEW_TOPIC -> exitDiscussion(
                    command.getRequestId(), pointer.getContextHandle());
            case SWITCH_PROJECT -> {
                if (context == null) {
                    yield discussionUnavailable(
                            command.getRequestId(),
                            "DISCUSSION_CONTEXT_EXPIRED");
                }
                String projectId = candidateProjectId(
                        proposal.getCandidateKey().orElseThrow(),
                        candidates);
                ProjectDiscussionCoordinator.Transition transition =
                        discussionCoordinator.switchProject(
                                context, projectId,
                                publicProjectIds(content),
                                session.expiresAt());
                yield executeDiscussionTransition(
                        command, cancellation, content, deadline,
                        pointer.getContextHandle(), transition,
                        modelExecution);
            }
            case REENTER_PROJECT -> {
                ProjectDiscussionCoordinator.Transition transition =
                        discussionCoordinator.reenter(
                                conversationId,
                                content.getContentVersion(),
                                pointer.getProjectId(),
                                publicProjectIds(content),
                                session.expiresAt());
                yield executeDiscussionTransition(
                        command, cancellation, content, deadline,
                        pointer.getContextHandle(), transition,
                        modelExecution);
            }
            case NEEDS_CLARIFICATION -> discussionClarification(
                    conversationId, session.tokenHash(), command.getRequestId(),
                    content, pointer, pointerStatus);
            case STANDARD_GOAL, ENTER_RECOMMENDED_RESULT ->
                    discussionInterpretationUnavailable(
                            command.getRequestId(), pointer);
        };
    }

    /**
     * 构造讨论方向澄清 challenge：ACTIVE 指针提供项目方面（facet）选项，
     * EXPIRED 指针只提供"重新进入项目"选项。challenge 携带 guard 变更，
     * 保证澄清期间讨论指针不被其他路径清除。
     */
    private Execution discussionClarification(
            String conversationId,
            byte[] tokenHash,
            UUID requestId,
            RuntimeAnswerContent content,
            ActiveDiscussionPointer pointer,
            ActiveDiscussionPointer.Status pointerStatus) {
        String clarificationId =
                "clarification_" + requestId.toString().replace("-", "");
        List<ClarificationChallenge.Choice> choices = new ArrayList<>();
        Map<String, String> bindings = new LinkedHashMap<>();
        Set<UserGoalProposal.Facet> facets;
        boolean reenter;
        if (pointerStatus == ActiveDiscussionPointer.Status.ACTIVE) {
            facets = Set.of(
                    UserGoalProposal.Facet.OVERVIEW,
                    UserGoalProposal.Facet.RESPONSIBILITY,
                    UserGoalProposal.Facet.SOLUTION,
                    UserGoalProposal.Facet.VERIFICATION,
                    UserGoalProposal.Facet.STATUS);
            for (UserGoalProposal.Facet facet : List.of(
                    UserGoalProposal.Facet.OVERVIEW,
                    UserGoalProposal.Facet.RESPONSIBILITY,
                    UserGoalProposal.Facet.SOLUTION,
                    UserGoalProposal.Facet.VERIFICATION,
                    UserGoalProposal.Facet.STATUS)) {
                String choiceId = "choice_facet_"
                        + facet.name().toLowerCase(java.util.Locale.ROOT);
                choices.add(new ClarificationChallenge.Choice(
                        choiceId, facetLabel(facet)));
                bindings.put(choiceId, "discussion:facet:" + facet.name());
            }
            reenter = false;
        } else {
            facets = Set.of();
            reenter = true;
            choices.add(new ClarificationChallenge.Choice(
                    "choice_reenter_project", "重新进入当前项目"));
            bindings.put("choice_reenter_project", "discussion:reenter");
        }
        ChallengeDefinition definition = choiceChallenge(
                clarificationId,
                pointerStatus == ActiveDiscussionPointer.Status.ACTIVE
                        ? "请选择要继续了解的项目方面。"
                        : "当前讨论已过期，是否重新进入该项目？",
                "field_discussion_direction",
                "讨论方向", choices, bindings);
        DiscussionClarificationTemplate template =
                new DiscussionClarificationTemplate(
                        pointer.getContextHandle(), pointer.getProjectId(),
                        facets, reenter);
        ClarificationStore.Record record = new ClarificationStore.Record(
                conversationId, tokenHash, content.getContentVersion(),
                definition.challenge(), definition.choiceBindings(),
                definition.textBindings(), template);
        PublicAgentTurn turn = new PublicAgentTurn.Clarification(
                requestId, "需要确认接下来的讨论方向。",
                definition.challenge(), List.of());
        return new Execution(
                turn, turn, turn, List.of(), List.of(record),
                DiscussionStateMutation.guard(pointer.getContextHandle()));
    }

    private String facetLabel(UserGoalProposal.Facet facet) {
        return switch (facet) {
            case OVERVIEW -> "项目概览";
            case BACKGROUND -> "项目背景";
            case RESPONSIBILITY -> "职责范围";
            case SOLUTION -> "解决方案";
            case VERIFICATION -> "验证方式";
            case STATUS -> "当前状态";
        };
    }

    /** 执行已预留的讨论澄清答案：facet 继续走目标执行并 guard 指针，reenter 走重进迁移。 */
    private Execution executeDiscussionClarification(
            String conversationId,
            ConversationSessionStore.Session session,
            AgentTurnCommand command,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            DiscussionClarificationResolution resolution,
            ResolvedModelExecution modelExecution) {
        if (session == null) {
            return discussionUnavailable(
                    command.getRequestId(), "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        ActiveDiscussionPointer pointer = session.activeDiscussion().orElse(null);
        DiscussionClarificationTemplate template = resolution.template();
        if (pointer == null
                || !pointer.getContextHandle().equals(template.getContextHandle())
                || !pointer.getProjectId().equals(template.getProjectId())) {
            return discussionUnavailable(
                    command.getRequestId(), "DISCUSSION_CONTEXT_MISMATCH");
        }
        if (resolution.facet() != null) {
            if (pointer.statusAt(clock.instant())
                    != ActiveDiscussionPointer.Status.ACTIVE
                    || !template.allowsFacet(resolution.facet())) {
                return discussionUnavailable(
                        command.getRequestId(), "DISCUSSION_CONTEXT_EXPIRED");
            }
            Execution execution = goals(
                    conversationId, new byte[0], command, cancellation, content,
                    planCompiler.compile(
                            discussionCoordinator.fact(
                                    template.getProjectId(), resolution.facet()),
                            content.getContentVersion(), resolutionContext(content),
                            audience(command)),
                    deadline.minus(settlementReserve), modelExecution);
            return withMutation(execution,
                    DiscussionStateMutation.guard(template.getContextHandle()));
        }
        if (!resolution.reenter() || !template.isReenterAllowed()) {
            return discussionUnavailable(
                    command.getRequestId(), "DISCUSSION_INTERPRETATION_UNAVAILABLE");
        }
        if (pointer.statusAt(clock.instant())
                != ActiveDiscussionPointer.Status.EXPIRED) {
            return discussionUnavailable(
                    command.getRequestId(), "DISCUSSION_CONTEXT_MISMATCH");
        }
        ProjectDiscussionCoordinator.Transition transition;
        try {
            transition = discussionCoordinator.reenter(
                        conversationId, content.getContentVersion(),
                        template.getProjectId(), publicProjectIds(content),
                        session.expiresAt());
        } catch (IllegalArgumentException unavailable) {
            return discussionUnavailable(
                    command.getRequestId(), "DISCUSSION_SUBJECT_UNAVAILABLE");
        }
        return executeDiscussionTransition(
                command, cancellation, content, deadline,
                template.getContextHandle(), transition, modelExecution);
    }

    /** 把讨论上下文的候选项目映射为受限路由候选（按键排序，过滤已下架项目）。 */
    private List<GoalInterpretationInput.RouteCandidate> routeCandidates(
            ProjectDiscussionContext context,
            RuntimeAnswerContent content) {
        List<GoalInterpretationInput.RouteCandidate> candidates =
                new ArrayList<>();
        int index = 1;
        for (String projectId :
                context.getSwitchCandidateProjectIds().stream()
                        .sorted().toList()) {
            AnswerKnowledge project = content.getProjects().stream()
                    .filter(value -> value.getStableId().equals(projectId))
                    .findFirst().orElse(null);
            if (project == null) continue;
            candidates.add(new GoalInterpretationInput.RouteCandidate(
                    "C" + index++,
                    GoalSubjectReference.Kind.PROJECT,
                    project.getStableId(),
                    project.getTitle(),
                    Set.of(
                            project.getStableId(),
                            project.getSlug(),
                            project.getTitle())));
        }
        return List.copyOf(candidates);
    }

    /**
     * 候选键 → 项目 ID 的受限映射。
     *
     * @throws IllegalArgumentException 候选键不在本次受限候选集内（模型越权）
     */
    private String candidateProjectId(
            String candidateKey,
            List<GoalInterpretationInput.RouteCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate.getCandidateKey()
                        .equals(candidateKey))
                .map(GoalInterpretationInput.RouteCandidate::getReference)
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException(
                                "candidate is outside discussion scope"));
    }

    private Execution withMutation(
            Execution execution,
            DiscussionStateMutation mutation) {
        return new Execution(
                execution.readOnlyTurn(),
                execution.settledTurn(),
                execution.replayTurn(),
                execution.contexts(),
                execution.challenges(),
                mutation,
                execution.clarificationMutation(),
                execution.semanticState());
    }

    private Execution withClarificationMutation(
            Execution execution,
            ClarificationSettlementMutation mutation) {
        if (mutation.isNone()) return execution;
        return new Execution(
                execution.readOnlyTurn(), execution.settledTurn(), execution.replayTurn(),
                execution.contexts(), execution.challenges(),
                execution.discussionMutation(), mutation,
                execution.semanticState());
    }

    private Execution withModelExecution(
            Execution execution,
            ModelExecutionProjection projection) {
        return new Execution(
                execution.readOnlyTurn().withModelExecution(projection),
                execution.settledTurn().withModelExecution(projection),
                execution.replayTurn().withModelExecution(projection),
                execution.contexts(), execution.challenges(),
                execution.discussionMutation(),
                execution.clarificationMutation(),
                execution.semanticState());
    }

    /** ENTER_RESULT：按 contextHandle 加载推荐上下文并进入指定结果的项目讨论。 */
    private Execution enterDiscussion(
            String conversationId,
            ConversationSessionStore.Session session,
            AgentTurnCommand.Continue command,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (session == null) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        ContinuationContext context = store.findContext(
                conversationId,
                command.getContextHandle().orElseThrow(),
                clock.instant(),
                deadline.minus(settlementReserve)).orElse(null);
        if (!(context instanceof ContinuationContext.Recommendation recommendation)) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        ProjectDiscussionCoordinator.Transition transition;
        try {
            transition = discussionCoordinator.enter(
                    conversationId,
                    content.getContentVersion(),
                    recommendation,
                    command.getResultItemId().orElseThrow(),
                    publicProjectIds(content),
                    session.expiresAt());
        } catch (IllegalArgumentException invalid) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        return executeDiscussionTransition(
                command, cancellation, content, deadline,
                session.activeDiscussion()
                        .map(ActiveDiscussionPointer::getContextHandle)
                        .orElse(null),
                transition, modelExecution);
    }

    /** REENTER_SUBJECT：在无活跃讨论时按主体引用重新进入项目讨论。 */
    private Execution reenterDiscussion(
            String conversationId,
            ConversationSessionStore.Session session,
            AgentTurnCommand.Continue command,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (session == null || command.getSubject().isEmpty()) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_UNAVAILABLE");
        }
        if (session.activeDiscussion()
                .map(pointer -> pointer.statusAt(clock.instant())
                        == ActiveDiscussionPointer.Status.ACTIVE)
                .orElse(false)) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_MISMATCH");
        }
        ProjectDiscussionCoordinator.Transition transition;
        try {
            transition = discussionCoordinator.reenter(
                    conversationId,
                    content.getContentVersion(),
                    command.getSubject().orElseThrow().getReference(),
                    publicProjectIds(content),
                    session.expiresAt());
        } catch (IllegalArgumentException invalid) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_SUBJECT_UNAVAILABLE");
        }
        return executeDiscussionTransition(
                command, cancellation, content, deadline,
                session.activeDiscussion()
                        .map(ActiveDiscussionPointer::getContextHandle)
                        .orElse(null),
                transition, modelExecution);
    }

    /**
     * 执行一次讨论状态迁移：先按迁移携带的概览目标走正常目标执行，成功（产出
     * Answer）后把新的讨论上下文并入 ContinuationContext，并把讨论指针变更设为
     * REPLACE（带期望世代，结算时做乐观并发校验）。
     */
    private Execution executeDiscussionTransition(
            AgentTurnCommand command,
            CancellationSignal cancellation,
            RuntimeAnswerContent content,
            TurnDeadline deadline,
            String expectedGeneration,
            ProjectDiscussionCoordinator.Transition transition,
            ResolvedModelExecution modelExecution) {
        Execution overview = goals(
                transition.context().getConversationId(),
                new byte[0],
                command,
                cancellation,
                content,
                planCompiler.compile(
                        transition.overviewGoal(),
                        content.getContentVersion(),
                        resolutionContext(content), audience(command)),
                deadline.minus(settlementReserve), modelExecution);
        if (!(overview.settledTurn() instanceof PublicAgentTurn.Answer)) {
            return overview;
        }
        List<ContinuationContext> contexts =
                new ArrayList<>(overview.contexts());
        contexts.add(transition.context());
        return new Execution(
                overview.readOnlyTurn(),
                overview.settledTurn(),
                overview.replayTurn(),
                List.copyOf(contexts),
                overview.challenges(),
                DiscussionStateMutation.replace(
                        expectedGeneration, transition.pointer()),
                overview.clarificationMutation(),
                overview.semanticState());
    }

    /** EXIT_CONTEXT 命令入口：校验 handle 世代匹配后清除讨论指针。 */
    private Execution exitDiscussion(
            ConversationSessionStore.Session session,
            AgentTurnCommand.Continue command) {
        ActiveDiscussionPointer pointer = session == null
                ? null : session.activeDiscussion().orElse(null);
        String expected = command.getContextHandle().orElseThrow();
        if (pointer == null || !pointer.matchesGeneration(expected)) {
            return discussionUnavailable(
                    command.getRequestId(),
                    "DISCUSSION_CONTEXT_MISMATCH");
        }
        return exitDiscussion(command.getRequestId(), expected);
    }

    /** 产出"已结束讨论"的会话式 Turn，并携带清除期望世代的讨论状态变更。 */
    private Execution exitDiscussion(
            UUID requestId, String expected) {
        PublicAgentTurn turn = new PublicAgentTurn.Conversational(
                requestId,
                "已结束当前项目讨论，你可以开始新的话题。",
                List.of());
        return new Execution(
                turn, turn, turn, List.of(), List.of(),
                DiscussionStateMutation.clear(expected));
    }

    /** 统一的讨论不可用降级 Turn（不可重试，提示重新进入项目）。 */
    private Execution discussionUnavailable(UUID requestId, String code) {
        PublicAgentTurn turn = new PublicAgentTurn.CapabilityUnavailable(
                requestId, code,
                "当前项目讨论状态不可用，请重新进入项目。",
                false, List.of());
        return serverFixed(turn);
    }

    /** 讨论请求解析不可用的降级 Turn（可重试，附带"结束讨论"建议动作）。 */
    private Execution discussionInterpretationUnavailable(
            UUID requestId, ActiveDiscussionPointer pointer) {
        PublicAgentTurn turn = new PublicAgentTurn.CapabilityUnavailable(
                requestId, "DISCUSSION_INTERPRETATION_UNAVAILABLE",
                "当前无法可靠理解这条项目讨论请求。",
                true, List.of(new SuggestedAction(
                        "discussion-exit", "结束讨论",
                        null, ContinuationReference.exitContext(
                        pointer.getContextHandle()))));
        return serverFixed(turn);
    }

    private Set<String> publicProjectIds(RuntimeAnswerContent content) {
        return content.getProjects().stream()
                .map(AnswerKnowledge::getStableId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * GOALS 主路径：编译计划 → 引擎执行 → 变更规划（ContextMutationPlanner 产出
     * 续跑上下文与 TTL）→ 三份投影（只读/结算/持久化安全重放）→ 会话语义状态投影。
     * 计划被拒或无法绑定公开主体时返回 Boundary 终态。
     */
    private Execution goals(
            String conversationId, byte[] resumeTokenHash, AgentTurnCommand command,
            CancellationSignal cancellation, RuntimeAnswerContent content,
            PlanCompilationResult compilation,
            TurnDeadline executionDeadline,
            ResolvedModelExecution modelExecution) {
        if (compilation.getKind() == PlanCompilationResult.Kind.CLARIFICATION_REQUIRED) {
            return serverFixed(new PublicAgentTurn.Boundary(
                    command.getRequestId(), "PLAN_SUBJECT_UNRESOLVED",
                    "当前目标无法安全绑定到公开主体，请重新提问。",
                    List.of(new SuggestedAction(
                            "ask-new-question", "重新提问", "请重新描述你的问题", null))));
        }
        if (compilation.getKind() != PlanCompilationResult.Kind.COMPILED) {
            return serverFixed(new PublicAgentTurn.Boundary(
                    command.getRequestId(), "PLAN_REJECTED",
                    "当前请求无法形成安全的执行计划。", List.of()));
        }
        ValidatedSemanticTurnPlan validated = compilation.getPlan().orElseThrow();
        SemanticTurnPlan plan = validated.getPlan();
        SemanticTurnOutcome outcome = engine.execute(
                validated, executionDeadline,
                cancellation, command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.Preset,
                modelExecution);
        List<ContextMutationPlanner.Mutation> mutations = mutationPlanner.plan(
                conversationId, plan, outcome,
                clock.instant().plus(contextTtl));
        Map<String, String> continuations = mutations.stream().collect(
                Collectors.toMap(
                        ContextMutationPlanner.Mutation::goalId,
                        value -> value.context().getContextHandle(),
                        (left, right) -> left, LinkedHashMap::new));
        PublicAgentTurn readOnly = projector.project(command.getRequestId(), plan, outcome);
        PublicAgentTurn settled = projector.project(
                command.getRequestId(), plan, outcome, continuations);
        PublicAgentTurn replay = replayPolicy.forPlan(settled, plan);
        ConversationSemanticState semanticState = semanticStateProjector.project(
                plan, settled, clock.instant());
        return new Execution(
                readOnly, settled, replay,
                mutations.stream().map(ContextMutationPlanner.Mutation::context).toList(),
                List.of(), DiscussionStateMutation.none(),
                ClarificationSettlementMutation.none(), semanticState);
    }

    /** CLARIFICATION 路径：按未闭合目标字段构造 challenge 记录与澄清 Turn。 */
    private Execution clarification(
            String conversationId, byte[] tokenHash, UUID requestId,
            RuntimeAnswerContent content, ClarificationProposal proposal) {
        String clarificationId = "clarification_" + requestId.toString().replace("-", "");
        GoalResolutionContext context = resolutionContext(content);
        if (proposal.getField() == ClarificationProposal.Field.SUBJECT
                && context.getPublicSubjects().isEmpty()) {
            return serverFixed(new PublicAgentTurn.CapabilityUnavailable(
                    requestId, "PUBLIC_SUBJECT_CATALOG_UNAVAILABLE",
                    "当前公开主体目录不可用，请稍后重试。", true, List.of()));
        }
        ChallengeDefinition definition = challengeDefinition(
                clarificationId, proposal.getBlockedGoal(), context);
        ClarificationChallenge challenge = definition.challenge();
        ClarificationStore.Record record = new ClarificationStore.Record(
                conversationId, tokenHash, content.getContentVersion(), challenge,
                definition.choiceBindings(), definition.textBindings(),
                proposal.getBlockedGoal());
        PublicAgentTurn turn = new PublicAgentTurn.Clarification(
                requestId, "需要补充目标后才能继续。", challenge, List.of());
        return new Execution(
                turn, turn, turn, List.of(), List.of(record),
                DiscussionStateMutation.none());
    }

    /**
     * 按未闭合字段生成澄清 challenge：REQUESTED_SIZE 给 1–5 数量选项，SUBJECT 给
     * 公开主体目录选项（上限 20），OUTPUT 按目标类别给期望产出选项，其余回退为
     * 文本补全。选项与文本分别绑定到结结构化 binding，供 resolve 时安全恢复目标。
     */
    private ChallengeDefinition challengeDefinition(
            String clarificationId,
            BlockedGoalTemplate blockedGoal,
            GoalResolutionContext context) {
        ClarificationProposal.Field field = blockedGoal.getUnresolvedField();
        String fieldId = "field_detail";
        if (field == ClarificationProposal.Field.REQUESTED_SIZE) {
            List<ClarificationChallenge.Choice> choices = java.util.stream.IntStream
                    .rangeClosed(1, 5)
                    .mapToObj(value -> new ClarificationChallenge.Choice(
                            "choice_size_" + value, value + " 个项目"))
                    .toList();
            Map<String, String> bindings = java.util.stream.IntStream.rangeClosed(1, 5)
                    .boxed().collect(Collectors.toMap(
                            value -> "choice_size_" + value,
                            value -> "size:" + value,
                            (left, right) -> left, LinkedHashMap::new));
            return choiceChallenge(
                    clarificationId, "请选择要推荐的项目数量（1—5 个）。",
                    fieldId, "推荐数量", choices, bindings);
        }
        if (field == ClarificationProposal.Field.SUBJECT) {
            List<GoalInterpretationInput.PublicSubjectDescriptor> subjects =
                    context.getPublicSubjects().stream().limit(20).toList();
            if (!subjects.isEmpty()) {
                List<ClarificationChallenge.Choice> choices = java.util.stream.IntStream
                        .range(0, subjects.size())
                        .mapToObj(index -> new ClarificationChallenge.Choice(
                                "choice_subject_" + (index + 1),
                                subjects.get(index).getLabel()))
                        .toList();
                Map<String, String> bindings = new LinkedHashMap<>();
                for (int index = 0; index < subjects.size(); index++) {
                    GoalInterpretationInput.PublicSubjectDescriptor value = subjects.get(index);
                    bindings.put(choices.get(index).choiceId(),
                            "subject:" + value.getKind().name() + ':' + value.getReference());
                }
                return choiceChallenge(
                        clarificationId, "请选择一个公开项目或案例。",
                        fieldId, "公开主体", choices, bindings);
            }
        }
        if (field == ClarificationProposal.Field.OUTPUT) {
            List<GoalRequestedOutput> outputs = switch (blockedGoal.getGoalKind()) {
                case PORTFOLIO_FACT -> List.of(
                        GoalRequestedOutput.OVERVIEW,
                        GoalRequestedOutput.BACKGROUND,
                        GoalRequestedOutput.RESPONSIBILITY,
                        GoalRequestedOutput.SOLUTION,
                        GoalRequestedOutput.VERIFICATION,
                        GoalRequestedOutput.STATUS);
                case PORTFOLIO_COMPARE -> List.of(GoalRequestedOutput.COMPARISON);
                case PORTFOLIO_RECOMMEND ->
                        List.of(GoalRequestedOutput.RECOMMENDATION);
                default -> List.of();
            };
            List<ClarificationChallenge.Choice> choices = outputs.stream()
                    .map(value -> new ClarificationChallenge.Choice(
                            "output_" + value.name().toLowerCase(java.util.Locale.ROOT),
                            switch (value) {
                                case OVERVIEW -> "项目概览";
                                case COMPARISON -> "项目比较";
                                case RECOMMENDATION -> "项目推荐";
                                default -> value.name();
                            }))
                    .toList();
            Map<String, String> bindings = new LinkedHashMap<>();
            for (int index = 0; index < outputs.size(); index++) {
                bindings.put(choices.get(index).choiceId(), "output:" + outputs.get(index).name());
            }
            return choiceChallenge(
                    clarificationId, "请选择期望的回答形式。",
                    fieldId, "回答形式", choices, bindings);
        }
        String prompt = "请从公开主体目录中补充一个项目或案例。";
        String label = "公开主体";
        String bindingKey = "subject:text";
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, prompt, List.of(
                new ClarificationChallenge.TextField(fieldId, label, true, 400)), List.of());
        return new ChallengeDefinition(
                challenge, Map.of(), Map.of(fieldId,
                new ClarificationStore.TextBinding(
                        bindingKey, 400)));
    }

    /** 单选 challenge 工厂：单一 SingleChoiceField + choiceId 到 binding 的映射。 */
    private ChallengeDefinition choiceChallenge(
            String clarificationId,
            String prompt,
            String fieldId,
            String label,
            List<ClarificationChallenge.Choice> choices,
            Map<String, String> bindings) {
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, prompt,
                List.of(new ClarificationChallenge.SingleChoiceField(
                        fieldId, label, true, choices)), List.of());
        return new ChallengeDefinition(
                challenge, Map.of(fieldId, Map.copyOf(bindings)), Map.of());
    }

    /** 服务端固定文案 Turn 的 Execution 工厂：三份投影一致、无上下文与变更。 */
    private Execution serverFixed(PublicAgentTurn turn) {
        return new Execution(
                turn, turn, turn, List.of(), List.of(),
                DiscussionStateMutation.none());
    }

    /** 模型选择解析失败的降级 Turn（不可重试，区分版本过期与不可用）。 */
    private Execution modelSelectionUnavailable(
            UUID requestId,
            ModelExecutionResolutionException.Code code) {
        String publicCode = switch (code) {
            case SELECTED_MODEL_UNAVAILABLE -> "SELECTED_MODEL_UNAVAILABLE";
            case MODEL_SELECTION_STALE -> "MODEL_SELECTION_STALE";
        };
        String message = code
                == ModelExecutionResolutionException.Code.MODEL_SELECTION_STALE
                ? "所选模型版本已更新，请刷新模型列表后重新选择。"
                : "所选模型当前不可用，请重新选择。";
        return serverFixed(new PublicAgentTurn.CapabilityUnavailable(
                requestId, publicCode, message, false, List.of()));
    }

    /** 所选模型执行失败的降级 Turn：按失败类别映射固定文案与可重试语义。 */
    private Execution selectedModelUnavailable(
            UUID requestId,
            SelectedModelFailureException failure) {
        String message = switch (failure.getCode()) {
            case SELECTED_MODEL_UNAVAILABLE ->
                    "所选模型当前不可用，请重新选择。";
            case SELECTED_MODEL_TEMPORARILY_UNAVAILABLE ->
                    "所选模型暂时不可用，请稍后使用新请求重试。";
            case SELECTED_MODEL_RATE_LIMITED ->
                    "所选模型请求过于频繁，请稍后使用新请求重试。";
            case SELECTED_MODEL_INVALID_RESPONSE ->
                    "所选模型返回了无法安全采用的结果，请更换模型或使用新请求重试。";
        };
        Long retryAfterSeconds = failure.getRetryAfterSeconds() == null
                ? null : failure.getRetryAfterSeconds().longValue();
        return serverFixed(new PublicAgentTurn.CapabilityUnavailable(
                requestId, failure.getCode().name(), message,
                failure.isRetryable(), retryAfterSeconds, List.of()));
    }

    /** Provider 派生回答体的 Execution 工厂：实时与结算保留原文，重放体替换为固定终端。 */
    private Execution providerBody(PublicAgentTurn turn) {
        return new Execution(
                turn, turn, replayPolicy.forProviderBody(turn),
                List.of(), List.of(), DiscussionStateMutation.none());
    }

    /**
     * 输入解析：ResolveClarification 在 stateExecutor 上限时预留 challenge 并按
     * 预留结果分类（IN_PROGRESS/不可用/成功），成功后按 resume 模板恢复为
     * typed 讨论选择、typed 讨论澄清或闭合目标（可继续下一轮澄清）；普通命令
     * 则按会话语义状态调用 GoalResolver。
     */
    private ResolvedInput resolveInput(
            String conversationId, byte[] tokenHash,
            ConversationSessionStore.Session sessionAuthority,
            AgentTurnCommand command, RuntimeAnswerContent content,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (command instanceof AgentTurnCommand.ResolveClarification clarification) {
            ClarificationStore.ClarificationAnswer answer =
                    clarification.getAnswer() instanceof AgentTurnCommand.ChoiceAnswer choice
                            ? new ClarificationStore.ClarificationAnswer.Choice(choice.getChoiceId())
                            : new ClarificationStore.ClarificationAnswer.Text(
                            ((AgentTurnCommand.TextAnswer) clarification.getAnswer()).getText());
            TurnDeadline operationDeadline = deadline.minus(settlementReserve);
            ClarificationStore.ReserveResult reserved;
            Future<ClarificationStore.ReserveResult> reserveTask = stateExecutor.submit(() ->
                    store.reserveClarification(
                            clarification.getClarificationId(), conversationId, tokenHash,
                            content.getContentVersion(), answer,
                            clarification.getRequestId(), deadline.getExpiresAt(),
                            clock.instant(), operationDeadline));
            try {
                long remainingMillis = operationDeadline.remainingMillis();
                if (remainingMillis < 1) {
                    reserveTask.cancel(true);
                    return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                            "当前澄清状态不可用，请重新提问。"));
                }
                reserved = reserveTask.get(remainingMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                reserveTask.cancel(true);
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"));
            } catch (InterruptedException interrupted) {
                reserveTask.cancel(true);
                Thread.currentThread().interrupt();
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"));
            } catch (ExecutionException | RuntimeException failure) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"));
            }
            if (reserved.status() != ClarificationStore.Status.RESERVED) {
                if (reserved.status() == ClarificationStore.Status.IN_PROGRESS) {
                    return new ResolvedInput(
                            ResolvedGoalSet.capabilityUnavailable(
                                    "当前澄清正在由另一请求处理，请稍后重新提交。"),
                            "CLARIFICATION_IN_PROGRESS",
                            reserved.retryAfterSeconds());
                }
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"));
            }
            ClarificationSettlementMutation mutation =
                    ClarificationSettlementMutation.consume(
                            clarification.getClarificationId(), answer);
            if (reserved.record().resumeTemplate() instanceof DiscussionSelectionTemplate selection) {
                String binding = reserved.answer().bindingKey();
                if (!binding.startsWith("result-item:")) {
                    return new ResolvedInput(ResolvedGoalSet.invalidInput(
                            "澄清答案无法绑定推荐结果。"), mutation);
                }
                String resultItemId = binding.substring("result-item:".length());
                if (!selection.allows(resultItemId)) {
                    return new ResolvedInput(ResolvedGoalSet.invalidInput(
                            "澄清答案超出推荐范围。"), mutation);
                }
                return new ResolvedInput(
                        ResolvedGoalSet.capabilityUnavailable("typed selection"),
                        new DiscussionSelectionResolution(
                                selection.getRecommendationContextHandle(), resultItemId),
                        null, mutation, null, null);
            }
            if (reserved.record().resumeTemplate()
                    instanceof DiscussionClarificationTemplate discussion) {
                String binding = reserved.answer().bindingKey();
                if (binding.equals("discussion:reenter")
                        && discussion.isReenterAllowed()) {
                    return new ResolvedInput(
                            ResolvedGoalSet.capabilityUnavailable("typed discussion clarification"),
                            null,
                            new DiscussionClarificationResolution(
                                    discussion, null, true),
                            mutation, null, null);
                }
                String prefix = "discussion:facet:";
                UserGoalProposal.Facet facet = null;
                if (binding.startsWith(prefix)) {
                    try {
                        facet = UserGoalProposal.Facet.valueOf(
                                binding.substring(prefix.length()));
                    } catch (IllegalArgumentException ignored) {
                        facet = null;
                    }
                }
                if (facet == null || !discussion.allowsFacet(facet)) {
                    return new ResolvedInput(ResolvedGoalSet.invalidInput(
                            "澄清答案超出当前讨论范围。"), mutation);
                }
                return new ResolvedInput(
                        ResolvedGoalSet.capabilityUnavailable(
                                "typed discussion clarification"),
                        null,
                        new DiscussionClarificationResolution(
                                discussion, facet, false),
                        mutation, null, null);
            }
            BlockedGoalTemplate template =
                    (BlockedGoalTemplate) reserved.record().resumeTemplate();
            ClarificationAnswerNormalizer normalizer = new ClarificationAnswerNormalizer();
            java.util.Optional<BlockedGoalTemplate.ResolutionValue> normalized =
                    normalizer.normalize(
                            template, reserved.answer(),
                            resolutionContext(content).getPublicSubjects());
            if (normalized.isEmpty()) {
                return new ResolvedInput(ResolvedGoalSet.invalidInput(
                        "澄清答案没有形成新的有效信息，请重新提问。"), mutation);
            }
            BlockedGoalTemplate.Resolution resolution = template.resolve(
                    normalized.orElseThrow());
            if (resolution.kind() == BlockedGoalTemplate.Resolution.Kind.NEXT_CLARIFICATION) {
                BlockedGoalTemplate next = resolution.continuation();
                return new ResolvedInput(ResolvedGoalSet.clarification(
                        new ClarificationProposal(
                                next.getUnresolvedField(),
                                "需要继续补充一个闭合目标字段。", next)), mutation);
            }
            if (resolution.kind() != BlockedGoalTemplate.Resolution.Kind.RESOLVED) {
                return new ResolvedInput(ResolvedGoalSet.invalidInput(
                        "澄清答案无法恢复为安全目标，请重新提问。"), mutation);
            }
            return new ResolvedInput(
                    ResolvedGoalSet.goals(resolution.proposal()), mutation);
        }
        ConversationSemanticState semanticState = sessionAuthority == null
                ? null : sessionAuthority.semanticState();
        return new ResolvedInput(semanticState == null
                ? goalResolver.resolve(
                command, resolutionContext(content),
                deadline.minus(settlementReserve), modelExecution)
                : goalResolver.resolve(
                command, resolutionContext(content),
                deadline.minus(settlementReserve), modelExecution,
                semanticState));
    }

    /** 由公开内容快照构造目标解析上下文：公开主体描述符与推荐约束白名单。 */
    private GoalResolutionContext resolutionContext(RuntimeAnswerContent content) {
        List<GoalInterpretationInput.PublicSubjectDescriptor> subjects = new ArrayList<>();
        addSubjects(subjects, content.getProjects(), GoalSubjectReference.Kind.PROJECT);
        addSubjects(subjects, content.getCases(), GoalSubjectReference.Kind.CASE);
        Set<String> recommendationConstraints = new java.util.LinkedHashSet<>();
        java.util.stream.Stream.concat(content.getProjects().stream(), content.getCases().stream())
                .forEach(value -> {
                    if (value.getCareerTrack() != null
                            && !"UNCLASSIFIED".equals(value.getCareerTrack())) {
                        recommendationConstraints.add(
                                "CAREER_TRACK_" + value.getCareerTrack());
                    }
                    value.getCapabilityCodes().forEach(code ->
                            recommendationConstraints.add("CAPABILITY_" + code));
                });
        return new GoalResolutionContext(
                subjects, Set.of(GoalKind.values()), Set.copyOf(recommendationConstraints));
    }

    private com.portfolio.agent.turn.planning.SemanticTaskParameters.AudienceProfile audience(
            AgentTurnCommand command) {
        return command.getSurfaceContext().getAudienceRole()
                .map(value -> com.portfolio.agent.turn.planning.SemanticTaskParameters
                        .AudienceProfile.valueOf(value.name()))
                .orElse(com.portfolio.agent.turn.planning.SemanticTaskParameters
                        .AudienceProfile.GUEST);
    }
    private void addSubjects(
            List<GoalInterpretationInput.PublicSubjectDescriptor> target,
            List<AnswerKnowledge> values, GoalSubjectReference.Kind kind) {
        values.forEach(value -> target.add(new GoalInterpretationInput.PublicSubjectDescriptor(
                kind, value.getStableId(), value.getTitle(),
                Set.of(value.getStableId(), value.getSlug(), value.getTitle()))));
    }
    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * 一次执行的完整结算载荷：三份公众投影（只读返回、结算写入、持久化安全重放）、
     * 续跑上下文、challenge 记录与三类结算变更（讨论/澄清/语义状态）。
     */
    private record Execution(
            PublicAgentTurn readOnlyTurn, PublicAgentTurn settledTurn,
            PublicAgentTurn replayTurn,
            List<com.portfolio.agent.turn.continuation.ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation,
            ConversationSemanticState semanticState) {
        private Execution(
                PublicAgentTurn readOnlyTurn,
                PublicAgentTurn settledTurn,
                PublicAgentTurn replayTurn,
                List<com.portfolio.agent.turn.continuation.ContinuationContext> contexts,
                List<ClarificationStore.Record> challenges,
                DiscussionStateMutation discussionMutation,
                ClarificationSettlementMutation clarificationMutation) {
            this(readOnlyTurn, settledTurn, replayTurn, contexts, challenges,
                    discussionMutation, clarificationMutation, null);
        }
        private Execution(
                PublicAgentTurn readOnlyTurn,
                PublicAgentTurn settledTurn,
                PublicAgentTurn replayTurn,
                List<com.portfolio.agent.turn.continuation.ContinuationContext> contexts,
                List<ClarificationStore.Record> challenges,
                DiscussionStateMutation discussionMutation) {
            this(readOnlyTurn, settledTurn, replayTurn, contexts, challenges,
                    discussionMutation,
                    ClarificationSettlementMutation.none(), null);
        }
    }
    /** 输入解析结果：目标集合，或 typed 讨论（选择/澄清）解析与澄清消费变更。 */
    private record ResolvedInput(
            ResolvedGoalSet resolved,
            DiscussionSelectionResolution discussionSelection,
            DiscussionClarificationResolution discussionClarification,
            ClarificationSettlementMutation clarificationMutation,
            String capabilityCode,
            Long retryAfterSeconds) {
        private ResolvedInput(ResolvedGoalSet resolved) {
            this(resolved, null, null, ClarificationSettlementMutation.none(),
                    null, null);
        }
        private ResolvedInput(
                ResolvedGoalSet resolved,
                ClarificationSettlementMutation clarificationMutation) {
            this(resolved, null, null, clarificationMutation, null, null);
        }
        private ResolvedInput(
                ResolvedGoalSet resolved,
                String capabilityCode,
                Long retryAfterSeconds) {
            this(resolved, null, null, ClarificationSettlementMutation.none(),
                    capabilityCode, retryAfterSeconds);
        }
    }
    /** 澄清恢复出的推荐结果选择：进入哪个上下文的哪个 resultItem。 */
    private record DiscussionSelectionResolution(
            String contextHandle, String resultItemId) { }
    /** 澄清恢复出的讨论方向：继续某个 facet 或重新进入项目。 */
    private record DiscussionClarificationResolution(
            DiscussionClarificationTemplate template,
            UserGoalProposal.Facet facet,
            boolean reenter) { }
    /** 一个澄清 challenge 及其选项/文本 binding 的完整定义。 */
    private record ChallengeDefinition(
            ClarificationChallenge challenge,
            Map<String, Map<String, String>> choiceBindings,
            Map<String, ClarificationStore.TextBinding> textBindings) { }
    /**
     * Turn 执行的对外结果：状态、Turn（状态为 COMPLETED/REPLAY 时非空）、
     * 建议等待秒数、结算是否失败降级，以及可选的会话元数据。
     */
    public record Result(Status status, PublicAgentTurn turn, long retryAfterSeconds,
                         boolean settlementFailed,
                         ConversationMetadata conversation,
                         SessionProjection sessionProjection) {
        public Result(
                Status status, PublicAgentTurn turn,
                long retryAfterSeconds, boolean settlementFailed,
                ConversationMetadata conversation) {
            this(status, turn, retryAfterSeconds,
                    settlementFailed, conversation, null);
        }
        /** 无 Turn 的纯状态结果工厂。 */
        static Result state(Status status, long retryAfter) {
            return new Result(
                    status, null, retryAfter, false, null, null);
        }
        Result withConversation(ConversationMetadata value) {
            return new Result(
                    status, turn, retryAfterSeconds, settlementFailed,
                    value, sessionProjection);
        }
    }
    /** 会话元数据：会话 ID、一次性签发的 ResumeToken、讨论修订号与摘要。 */
    public record ConversationMetadata(
            String conversationId, String resumeToken,
            long discussionRevision,
            DiscussionSummary discussion) {
        public ConversationMetadata(
                String conversationId, String resumeToken) {
            this(conversationId, resumeToken, 0, null);
        }
    }
    /** 当前会话查询结果：是否认证、会话 ID、讨论修订号与活跃讨论摘要。 */
    public record ConversationStatus(
            boolean authenticated,
            String conversationId,
            long discussionRevision,
            DiscussionSummary discussion) {
        public ConversationStatus(
                boolean authenticated, String conversationId) {
            this(authenticated, conversationId, 0, null);
        }
        public ConversationStatus(
                boolean authenticated, String conversationId,
                DiscussionSummary discussion) {
            this(authenticated, conversationId, 0, discussion);
        }
    }
    /** 会话投影：活跃讨论指针与讨论修订号。 */
    public record SessionProjection(
            ActiveDiscussionPointer pointer,
            long revision) { }
    /** 对外讨论摘要：状态、项目、标题、路由、过期时间与上下文 Handle。 */
    public record DiscussionSummary(
            ActiveDiscussionPointer.Status status,
            String projectId,
            String label,
            String route,
            Instant expiresAt,
            String contextHandle) { }
    /** Turn 对控制器的七种终态；IN_PROGRESS/CONFLICT/UNAUTHORIZED 携带重试或拒绝语义。 */
    public enum Status {
        COMPLETED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED, STORE_UNAVAILABLE, UNAUTHORIZED
    }
    /** 取消请求的五种结果。 */
    public enum CancelStatus {
        CANCELLED, ALREADY_COMPLETED, NOT_FOUND, UNAUTHORIZED, STORE_UNAVAILABLE
    }
}
