package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
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

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final ActiveTurnRegistry activeTurns = new ActiveTurnRegistry();
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration executionDuration;
    private final Duration contextTtl;

    public AgentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler, SemanticTurnEngine engine,
            PublicAgentTurnProjector projector, ContextMutationPlanner mutationPlanner,
            AgentStateStore store, RequestFingerprintFactory fingerprintFactory,
            ConversationSessionResolver sessionResolver,
            Clock clock, Duration leaseDuration,
            Duration executionDuration, Duration contextTtl) {
        this.knowledgeGateway = java.util.Objects.requireNonNull(knowledgeGateway);
        this.goalResolver = java.util.Objects.requireNonNull(goalResolver);
        this.planCompiler = java.util.Objects.requireNonNull(planCompiler);
        this.engine = java.util.Objects.requireNonNull(engine);
        this.projector = java.util.Objects.requireNonNull(projector);
        this.mutationPlanner = java.util.Objects.requireNonNull(mutationPlanner);
        this.store = java.util.Objects.requireNonNull(store);
        this.fingerprintFactory = java.util.Objects.requireNonNull(fingerprintFactory);
        this.sessionResolver = java.util.Objects.requireNonNull(sessionResolver);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.executionDuration = positive(executionDuration, "executionDuration");
        this.contextTtl = positive(contextTtl, "contextTtl");
    }

    public Result execute(String bearerToken, AgentTurnCommand command) {
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, command.getRequestId());
        if (session.status() == ConversationSessionResolver.Status.INVALID) {
            return Result.state(Status.UNAUTHORIZED, 0);
        }
        Result result = executeResolved(
                session.conversationId(), session.tokenHash(),
                sessionResolver.pendingSession(session), command);
        boolean canCommitSession = (result.status() == Status.COMPLETED
                || result.status() == Status.REPLAY) && !result.settlementFailed();
        if (canCommitSession) sessionResolver.commit(session);
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
            AgentTurnCommand command) {
        byte[] fingerprint = fingerprintFactory.fingerprint(command);
        TurnExecutionStore.ClaimResult claim;
        try {
            claim = store.claim(
                    command.getRequestId(), conversationId, fingerprint,
                    clock.instant(), leaseDuration);
        } catch (RuntimeException failure) {
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
                    conversationId, resumeTokenHash, command, cancellation);
            try {
                boolean completed = store.complete(
                        command.getRequestId(), fingerprint, execution.settledTurn(),
                        execution.contexts(), execution.challenges(),
                        sessionToCreate, clock.instant());
                if (!completed) return Result.state(Status.CANCELLED, 0);
                return new Result(Status.COMPLETED, execution.settledTurn(), 0, false, null);
            } catch (RuntimeException settlementFailure) {
                return new Result(Status.COMPLETED, execution.readOnlyTurn(), 0, true, null);
            }
        } finally {
            activeTurns.releaseOwner(command.getRequestId(), cancelAction);
        }
    }

    public CancelStatus cancel(String bearerToken, UUID requestId) {
        ConversationSessionResolver.Resolution session = sessionResolver.resolve(bearerToken, requestId);
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
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID());
        return session.status() == ConversationSessionResolver.Status.AUTHENTICATED
                ? new ConversationStatus(true, session.conversationId())
                : new ConversationStatus(false, null);
    }

    public boolean clearConversation(String bearerToken) {
        ConversationSessionResolver.Resolution session =
                sessionResolver.resolve(bearerToken, UUID.randomUUID());
        if (session.status() != ConversationSessionResolver.Status.AUTHENTICATED) return false;
        store.clearConversation(session.conversationId());
        sessionResolver.clear(session);
        return true;
    }

    private Execution executeClaimed(
            String conversationId, byte[] resumeTokenHash,
            AgentTurnCommand command, CancellationSignal cancellation) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ResolvedInput input = resolveInput(
                conversationId, resumeTokenHash, command, content);
        ResolvedGoalSet resolved = input.resolved();
        return switch (resolved.getKind()) {
            case CONVERSATIONAL -> simple(new PublicAgentTurn.Conversational(
                    command.getRequestId(), resolved.getMessage().orElseThrow(), List.of()));
            case BOUNDARY, INVALID_INPUT -> simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                    ? "PUBLIC_SUBJECT_INVALID" : "OUT_OF_SCOPE",
                    resolved.getMessage().orElseThrow(), List.of()));
            case CAPABILITY_UNAVAILABLE -> simple(new PublicAgentTurn.CapabilityUnavailable(
                    command.getRequestId(), command instanceof AgentTurnCommand.Ask
                    ? "GOAL_INTERPRETATION_UNAVAILABLE" : "CONTINUATION_UNAVAILABLE",
                    resolved.getMessage().orElseThrow(), command instanceof AgentTurnCommand.Ask, List.of()));
            case CLARIFICATION -> clarification(
                    conversationId, resumeTokenHash, command.getRequestId(),
                    content.getContentVersion(), resolved.getClarification().orElseThrow());
            case GOALS -> goals(
                    conversationId, resumeTokenHash, command, cancellation, content,
                    input.parentHandlesByGoal(),
                    planCompiler.compile(
                            resolved.getGoalProposal().orElseThrow(), content.getContentVersion(),
                            resolutionContext(content)));
        };
    }

    private Execution goals(
            String conversationId, byte[] resumeTokenHash, AgentTurnCommand command,
            CancellationSignal cancellation, RuntimeAnswerContent content,
            Map<String, String> parentHandlesByGoal,
            PlanCompilationResult compilation) {
        if (compilation.getKind() == PlanCompilationResult.Kind.CLARIFICATION_REQUIRED) {
            return clarification(
                    conversationId, resumeTokenHash, command.getRequestId(), content.getContentVersion(),
                    new ClarificationProposal(
                            ClarificationProposal.Field.SUBJECT,
                            "需要明确公开主体后才能继续。",
                            new com.portfolio.agent.turn.planning.UserGoalProposal.InputAnchor("目标", 0)));
        }
        if (compilation.getKind() != PlanCompilationResult.Kind.COMPILED) {
            return simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), "PLAN_REJECTED",
                    "当前请求无法形成安全的执行计划。", List.of()));
        }
        ValidatedSemanticTurnPlan validated = compilation.getPlan().orElseThrow();
        SemanticTurnPlan plan = validated.getPlan();
        SemanticTurnOutcome outcome = engine.execute(
                validated, TurnDeadline.after(executionDuration, clock),
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
            String release, ClarificationProposal proposal) {
        String clarificationId = "clarification_" + requestId.toString().replace("-", "");
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, proposal.getPrompt(), List.of(
                new ClarificationChallenge.TextField("field_detail", "补充目标", true, 400)), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                conversationId, tokenHash, release, challenge,
                Map.of(), Map.of("field_detail", new ClarificationStore.TextBinding(
                "goal:" + proposal.getField().name(), 400)));
        PublicAgentTurn turn = new PublicAgentTurn.Clarification(
                requestId, "需要补充目标后才能继续。", challenge, List.of());
        return new Execution(turn, turn, List.of(), List.of(record));
    }

    private Execution simple(PublicAgentTurn turn) {
        return new Execution(turn, turn, List.of(), List.of());
    }

    private ResolvedInput resolveInput(
            String conversationId, byte[] tokenHash,
            AgentTurnCommand command, RuntimeAnswerContent content) {
        if (command instanceof AgentTurnCommand.Continue continuation) {
            ContinuationContext context;
            try {
                context = store.findContext(
                        conversationId, continuation.getContextHandle(), clock.instant()).orElse(null);
            } catch (RuntimeException failure) {
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
            try {
                consumed = store.consumeClarification(
                        clarification.getClarificationId(), conversationId, tokenHash,
                        content.getContentVersion(), answer, clock.instant());
            } catch (RuntimeException failure) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            }
            if (consumed.status() != ClarificationStore.Status.CONSUMED) {
                return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                        "当前澄清状态不可用，请重新提问。"), Map.of());
            }
            if (answer instanceof ClarificationStore.ClarificationAnswer.Text text) {
                AgentTurnCommand.Ask ask = new AgentTurnCommand.Ask(
                        command.getRequestId(), new AgentTurnCommand.FreeText(text.text()),
                        command.getSurfaceContext(), command.getConversationWindow());
                return new ResolvedInput(
                        goalResolver.resolve(ask, resolutionContext(content)), Map.of());
            }
            return new ResolvedInput(ResolvedGoalSet.capabilityUnavailable(
                    "当前选择无法恢复为安全目标，请重新提问。"), Map.of());
        }
        return new ResolvedInput(
                goalResolver.resolve(command, resolutionContext(content)), Map.of());
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
