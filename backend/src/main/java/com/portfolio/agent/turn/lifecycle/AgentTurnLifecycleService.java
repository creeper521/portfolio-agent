package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
import com.portfolio.agent.turn.continuation.ClarificationAnswerNormalizer;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.ContinuationReference;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalResolutionContext;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.PlanCompilationResult;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.ValidatedSemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
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

/** Claim -> resolve -> plan -> execute -> project -> single settlement lifecycle. */
public final class AgentTurnLifecycleService {
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
    private final ActiveTurnRegistry activeTurns = new ActiveTurnRegistry();
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration turnTimeout;
    private final Duration settlementReserve;
    private final Duration contextTtl;

    public AgentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler, SemanticTurnEngine engine,
            PublicAgentTurnProjector projector, ContextMutationPlanner mutationPlanner,
            AgentStateStore store, RequestFingerprintFactory fingerprintFactory,
            ConversationSessionResolver sessionResolver,
            ExecutorService stateExecutor,
            Clock clock, Duration leaseDuration,
            Duration turnTimeout, Duration settlementReserve,
            Duration contextTtl) {
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
    }

    public Result execute(String bearerToken, AgentTurnCommand command) {
        Instant turnStartedAt = clock.instant();
        TurnDeadline turnDeadline = new TurnDeadline(
                turnStartedAt.plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, command.getRequestId(), turnDeadline);
        if (session.status() == ConversationSessionResolver.Status.INVALID) {
            return Result.state(Status.UNAUTHORIZED, 0);
        }
        Result result = executeResolved(
                session.conversationId(), session.tokenHash(),
                sessionResolver.pendingSession(session), command,
                turnStartedAt, turnDeadline);
        boolean canCommitSession = (result.status() == Status.COMPLETED
                || result.status() == Status.REPLAY) && !result.settlementFailed();
        if (canCommitSession || session.status() == ConversationSessionResolver.Status.AUTHENTICATED) {
            return result.withConversation(new ConversationMetadata(
                    session.conversationId(), session.issuedToken() == null
                    ? null : session.issuedToken().encode()));
        }
        return result;
    }

    private Result executeResolved(
            String conversationId, byte[] resumeTokenHash,
            ConversationSessionStore.Session sessionToCreate,
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
            case REPLAY: return new Result(Status.REPLAY, claim.replay(), 0, false, null);
            case IN_PROGRESS: return Result.state(Status.IN_PROGRESS, claim.retryAfterSeconds());
            case CONFLICT: return Result.state(Status.CONFLICT, 0);
            case CANCELLED: return Result.state(Status.CANCELLED, 0);
            case CLAIMED: break;
        }
        CancellationSignal cancellation = new CancellationSignal();
        Runnable cancelAction = cancellation::cancel;
        activeTurns.claimOwner(command.getRequestId(), cancelAction);
        try {
            Execution execution = executeClaimed(
                    conversationId, resumeTokenHash, command, cancellation,
                    turnDeadline);
            return settle(
                    command.getRequestId(), fingerprint,
                    execution, sessionToCreate, sessionAccess, turnDeadline);
        } finally {
            activeTurns.releaseOwner(command.getRequestId(), cancelAction);
        }
    }

    private Result settle(
            UUID requestId, byte[] fingerprint,
            Execution execution, ConversationSessionStore.Session sessionToCreate,
            TurnExecutionStore.SessionAccess sessionAccess,
            TurnDeadline turnDeadline) {
        long remainingMillis = turnDeadline.remainingMillis();
        if (remainingMillis < 1) {
            return settlementFailed(execution);
        }
        Future<Boolean> settlement = stateExecutor.submit(
                () -> store.complete(
                        requestId, fingerprint, execution.settledTurn(),
                        execution.contexts(), execution.challenges(),
                        sessionToCreate, sessionAccess, clock.instant(), turnDeadline));
        try {
            boolean completed = settlement.get(remainingMillis, TimeUnit.MILLISECONDS);
            if (!completed) return Result.state(Status.CANCELLED, 0);
            return new Result(Status.COMPLETED, execution.settledTurn(), 0, false, null);
        } catch (TimeoutException timeout) {
            settlement.cancel(true);
            return settlementFailed(execution);
        } catch (InterruptedException interrupted) {
            settlement.cancel(true);
            Result result = settlementFailed(execution);
            Thread.currentThread().interrupt();
            return result;
        } catch (ExecutionException failure) {
            return settlementFailed(execution);
        }
    }

    private Result settlementFailed(Execution execution) {
        return new Result(
                Status.COMPLETED, execution.readOnlyTurn(), 0, true, null);
    }

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

    public ConversationStatus currentConversation(String bearerToken) {
        TurnDeadline deadline = new TurnDeadline(
                clock.instant().plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID(), deadline);
        return session.status() == ConversationSessionResolver.Status.AUTHENTICATED
                ? new ConversationStatus(true, session.conversationId())
                : new ConversationStatus(false, null);
    }

    public boolean clearConversation(String bearerToken) {
        TurnDeadline deadline = new TurnDeadline(
                clock.instant().plus(turnTimeout), clock);
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID(), deadline);
        if (session.status() != ConversationSessionResolver.Status.AUTHENTICATED) return false;
        return store.clearConversation(
                session.conversationId(), session.tokenHash(), clock.instant());
    }

    private Execution executeClaimed(
            String conversationId, byte[] resumeTokenHash,
            AgentTurnCommand command, CancellationSignal cancellation,
            TurnDeadline turnDeadline) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ResolvedInput input = resolveInput(
                conversationId, resumeTokenHash, command, content,
                turnDeadline.minus(settlementReserve));
        ResolvedGoalSet resolved = input.resolved();
        return switch (resolved.getKind()) {
            case CONVERSATIONAL -> simple(new PublicAgentTurn.Conversational(
                    command.getRequestId(), resolved.getMessage().orElseThrow(), List.of()));
            case BOUNDARY, INVALID_INPUT -> simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                    ? "PUBLIC_SUBJECT_INVALID" : "OUT_OF_SCOPE",
                    resolved.getMessage().orElseThrow(),
                    resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                            ? List.of(new SuggestedAction(
                            "ask-new-question", "重新提问", "请重新描述你的问题", null))
                            : List.of()));
            case CAPABILITY_UNAVAILABLE -> simple(new PublicAgentTurn.CapabilityUnavailable(
                    command.getRequestId(), command instanceof AgentTurnCommand.Ask
                    ? "GOAL_INTERPRETATION_UNAVAILABLE" : "CONTINUATION_UNAVAILABLE",
                    resolved.getMessage().orElseThrow(), command instanceof AgentTurnCommand.Ask, List.of()));
            case CLARIFICATION -> clarification(
                    conversationId, resumeTokenHash, command.getRequestId(),
                    content, resolved.getClarification().orElseThrow());
            case GOALS -> goals(
                    conversationId, resumeTokenHash, command, cancellation, content,
                    input.parentHandlesByGoal(),
                    planCompiler.compile(
                            resolved.getGoalProposal().orElseThrow(), content.getContentVersion(),
                            resolutionContext(content)),
                    turnDeadline.minus(settlementReserve));
        };
    }

    private Execution goals(
            String conversationId, byte[] resumeTokenHash, AgentTurnCommand command,
            CancellationSignal cancellation, RuntimeAnswerContent content,
            Map<String, String> parentHandlesByGoal,
            PlanCompilationResult compilation,
            TurnDeadline executionDeadline) {
        if (compilation.getKind() == PlanCompilationResult.Kind.CLARIFICATION_REQUIRED) {
            return simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), "PLAN_SUBJECT_UNRESOLVED",
                    "当前目标无法安全绑定到公开主体，请重新提问。",
                    List.of(new SuggestedAction(
                            "ask-new-question", "重新提问", "请重新描述你的问题", null))));
        }
        if (compilation.getKind() != PlanCompilationResult.Kind.COMPILED) {
            return simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), "PLAN_REJECTED",
                    "当前请求无法形成安全的执行计划。", List.of()));
        }
        ValidatedSemanticTurnPlan validated = compilation.getPlan().orElseThrow();
        SemanticTurnPlan plan = validated.getPlan();
        SemanticTurnOutcome outcome = engine.execute(
                validated, executionDeadline,
                cancellation, command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.Preset);
        List<ContextMutationPlanner.Mutation> mutations = mutationPlanner.plan(
                conversationId, plan, outcome, clock.instant().plus(contextTtl),
                parentHandlesByGoal);
        Map<String, ContinuationReference> continuations = mutations.stream().collect(
                Collectors.toMap(
                        ContextMutationPlanner.Mutation::goalId,
                        value -> new ContinuationReference(
                                value.context().getContextHandle(), null),
                        (left, right) -> left, LinkedHashMap::new));
        PublicAgentTurn readOnly = projector.project(command.getRequestId(), plan, outcome);
        PublicAgentTurn settled = projector.project(
                command.getRequestId(), plan, outcome, continuations);
        return new Execution(
                readOnly, settled,
                mutations.stream().map(ContextMutationPlanner.Mutation::context).toList(), List.of());
    }

    private Execution clarification(
            String conversationId, byte[] tokenHash, UUID requestId,
            RuntimeAnswerContent content, ClarificationProposal proposal) {
        String clarificationId = "clarification_" + requestId.toString().replace("-", "");
        GoalResolutionContext context = resolutionContext(content);
        if (proposal.getField() == ClarificationProposal.Field.SUBJECT
                && context.getPublicSubjects().isEmpty()) {
            return simple(new PublicAgentTurn.CapabilityUnavailable(
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
        return new Execution(turn, turn, List.of(), List.of(record));
    }

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
                case PORTFOLIO_RECOMMEND, PORTFOLIO_REFINE_RECOMMENDATION ->
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

    private Execution simple(PublicAgentTurn turn) {
        return new Execution(turn, turn, List.of(), List.of());
    }

    private ResolvedInput resolveInput(
            String conversationId, byte[] tokenHash,
            AgentTurnCommand command, RuntimeAnswerContent content,
            TurnDeadline deadline) {
        if (command instanceof AgentTurnCommand.Continue continuation) {
            ContinuationContext context;
            Future<Optional<ContinuationContext>> contextTask = stateExecutor.submit(() ->
                    store.findContext(
                            conversationId, continuation.getContextHandle(),
                            clock.instant(), deadline));
            try {
                long remainingMillis = deadline.remainingMillis();
                if (remainingMillis < 1) {
                    contextTask.cancel(true);
                    return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                            "当前续接状态不可用，请重新提问。"), Map.of());
                }
                context = contextTask.get(remainingMillis, TimeUnit.MILLISECONDS).orElse(null);
            } catch (TimeoutException timeout) {
                contextTask.cancel(true);
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前续接状态不可用，请重新提问。"), Map.of());
            } catch (InterruptedException interrupted) {
                contextTask.cancel(true);
                Thread.currentThread().interrupt();
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前续接状态不可用，请重新提问。"), Map.of());
            } catch (ExecutionException | RuntimeException failure) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前续接状态不可用，请重新提问。"), Map.of());
            }
            if (context == null || !resultItemValid(context, continuation.getResultItemId().orElse(null))) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前续接状态不可用，请重新提问。"), Map.of());
            }
            UserGoalProposal proposal = continuationProposal(continuation, context);
            return new ResolvedInput(
                    ResolvedGoalSet.goals(proposal),
                    Map.of("continuation-goal", context.getContextHandle()));
        }
        if (command instanceof AgentTurnCommand.ResolveClarification clarification) {
            ClarificationStore.ClarificationAnswer answer =
                    clarification.getAnswer() instanceof AgentTurnCommand.ChoiceAnswer choice
                            ? new ClarificationStore.ClarificationAnswer.Choice(choice.getChoiceId())
                            : new ClarificationStore.ClarificationAnswer.Text(
                            ((AgentTurnCommand.TextAnswer) clarification.getAnswer()).getText());
            ClarificationStore.ConsumeResult consumed;
            Future<ClarificationStore.ConsumeResult> consumeTask = stateExecutor.submit(() ->
                    store.consumeClarification(
                            clarification.getClarificationId(), conversationId, tokenHash,
                            content.getContentVersion(), answer, clock.instant(), deadline));
            try {
                long remainingMillis = deadline.remainingMillis();
                if (remainingMillis < 1) {
                    consumeTask.cancel(true);
                    return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                            "当前澄清状态不可用，请重新提问。"), Map.of());
                }
                consumed = consumeTask.get(remainingMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                consumeTask.cancel(true);
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            } catch (InterruptedException interrupted) {
                consumeTask.cancel(true);
                Thread.currentThread().interrupt();
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            } catch (ExecutionException | RuntimeException failure) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            }
            if (consumed.status() != ClarificationStore.Status.CONSUMED) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            }
            BlockedGoalTemplate template = consumed.record().blockedGoal();
            ClarificationAnswerNormalizer normalizer = new ClarificationAnswerNormalizer();
            java.util.Optional<BlockedGoalTemplate.ResolutionValue> normalized =
                    normalizer.normalize(
                            template, consumed.answer(),
                            resolutionContext(content).getPublicSubjects());
            if (normalized.isEmpty()) {
                return new ResolvedInput(ResolvedGoalSet.invalidInput(
                        "澄清答案没有形成新的有效信息，请重新提问。"), Map.of());
            }
            BlockedGoalTemplate.Resolution resolution = template.resolve(
                    normalized.orElseThrow());
            if (resolution.kind() == BlockedGoalTemplate.Resolution.Kind.NEXT_CLARIFICATION) {
                BlockedGoalTemplate next = resolution.continuation();
                return new ResolvedInput(ResolvedGoalSet.clarification(
                        new ClarificationProposal(
                                next.getUnresolvedField(),
                                "需要继续补充一个闭合目标字段。", next)), Map.of());
            }
            if (resolution.kind() != BlockedGoalTemplate.Resolution.Kind.RESOLVED) {
                return new ResolvedInput(ResolvedGoalSet.invalidInput(
                        "澄清答案无法恢复为安全目标，请重新提问。"), Map.of());
            }
            return new ResolvedInput(
                    ResolvedGoalSet.goals(resolution.proposal()), Map.of());
        }
        return new ResolvedInput(
                goalResolver.resolve(command, resolutionContext(content), deadline), Map.of());
    }

    private boolean resultItemValid(ContinuationContext context, String resultItemId) {
        if (resultItemId == null) return true;
        return context instanceof ContinuationContext.Recommendation recommendation
                && recommendation.getSelectedResults().stream().anyMatch(value ->
                value.resultItemId().equals(resultItemId));
    }

    private UserGoalProposal continuationProposal(
            AgentTurnCommand.Continue command, ContinuationContext context) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(command.getText(), 0);
        List<GoalSubjectReference> subjects;
        GoalKind kind;
        GoalRequestedOutput output;
        UserGoalProposal.GoalParameters parameters;
        if (context instanceof ContinuationContext.PortfolioFact fact) {
            subjects = subjects(fact.getSubjectIds());
            kind = GoalKind.PORTFOLIO_FACT;
            output = GoalRequestedOutput.OVERVIEW;
            parameters = new UserGoalProposal.PortfolioFactParameters(
                    fact.getFacets().stream().map(UserGoalProposal.Facet::valueOf)
                            .collect(Collectors.toSet()));
        } else if (context instanceof ContinuationContext.PortfolioComparison comparison) {
            subjects = subjects(comparison.getSubjectIds());
            kind = GoalKind.PORTFOLIO_COMPARE;
            output = GoalRequestedOutput.COMPARISON;
            parameters = new UserGoalProposal.PortfolioCompareParameters(comparison.getDimensions());
        } else {
            ContinuationContext.Recommendation recommendation =
                    (ContinuationContext.Recommendation) context;
            Set<String> selected = command.getResultItemId().flatMap(item ->
                    recommendation.getSelectedResults().stream()
                            .filter(value -> value.resultItemId().equals(item)).findFirst()
                            .map(ContinuationContext.ResultItem::subjectId)).stream()
                    .collect(Collectors.toSet());
            Set<String> scope = !selected.isEmpty() ? selected
                    : recommendation.isAllPublishedAuthorized()
                    ? recommendation.getSelectedResults().stream()
                    .map(ContinuationContext.ResultItem::subjectId).collect(Collectors.toSet())
                    : recommendation.getAuthorizedSubjectIds();
            subjects = subjects(scope);
            kind = GoalKind.PORTFOLIO_REFINE_RECOMMENDATION;
            output = GoalRequestedOutput.RECOMMENDATION;
            parameters = new UserGoalProposal.PortfolioRefineParameters(Set.of("USER_REFINEMENT"));
        }
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "continuation-goal", kind, anchor, subjects, Set.of(output),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE, parameters)));
    }

    private List<GoalSubjectReference> subjects(Set<String> ids) {
        return ids.stream().sorted().map(value -> new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, value,
                GoalSubjectReference.Basis.CONTINUATION, null)).toList();
    }

    private GoalResolutionContext resolutionContext(RuntimeAnswerContent content) {
        List<GoalInterpretationInput.PublicSubjectDescriptor> subjects = new ArrayList<>();
        addSubjects(subjects, content.getProjects(), GoalSubjectReference.Kind.PROJECT);
        addSubjects(subjects, content.getCases(), GoalSubjectReference.Kind.CASE);
        return new GoalResolutionContext(subjects, Set.of(GoalKind.values()));
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

    private record Execution(
            PublicAgentTurn readOnlyTurn, PublicAgentTurn settledTurn,
            List<com.portfolio.agent.turn.continuation.ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges) { }
    private record ResolvedInput(
            ResolvedGoalSet resolved, Map<String, String> parentHandlesByGoal) { }
    private record ChallengeDefinition(
            ClarificationChallenge challenge,
            Map<String, Map<String, String>> choiceBindings,
            Map<String, ClarificationStore.TextBinding> textBindings) { }
    public record Result(Status status, PublicAgentTurn turn, long retryAfterSeconds,
                         boolean settlementFailed, ConversationMetadata conversation) {
        static Result state(Status status, long retryAfter) {
            return new Result(status, null, retryAfter, false, null);
        }
        Result withConversation(ConversationMetadata value) {
            return new Result(status, turn, retryAfterSeconds, settlementFailed, value);
        }
    }
    public record ConversationMetadata(String conversationId, String resumeToken) { }
    public record ConversationStatus(boolean authenticated, String conversationId) { }
    public enum Status {
        COMPLETED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED, STORE_UNAVAILABLE, UNAUTHORIZED
    }
    public enum CancelStatus {
        CANCELLED, ALREADY_COMPLETED, NOT_FOUND, UNAUTHORIZED, STORE_UNAVAILABLE
    }
}
