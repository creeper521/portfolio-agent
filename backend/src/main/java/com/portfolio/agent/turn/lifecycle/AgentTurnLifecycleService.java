package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ContinuationReference;
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
    private final TurnExecutionStore store;
    private final RequestFingerprintFactory fingerprintFactory;
    private final ActiveTurnRegistry activeTurns = new ActiveTurnRegistry();
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration executionDuration;
    private final Duration contextTtl;

    public AgentTurnLifecycleService(
            PortfolioKnowledgeGateway knowledgeGateway, GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler, SemanticTurnEngine engine,
            PublicAgentTurnProjector projector, ContextMutationPlanner mutationPlanner,
            TurnExecutionStore store, RequestFingerprintFactory fingerprintFactory,
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
        this.clock = java.util.Objects.requireNonNull(clock);
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.executionDuration = positive(executionDuration, "executionDuration");
        this.contextTtl = positive(contextTtl, "contextTtl");
    }

    public Result execute(String conversationId, byte[] resumeTokenHash, AgentTurnCommand command) {
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
            case REPLAY: return new Result(Status.REPLAY, claim.replay(), 0, false);
            case IN_PROGRESS: return Result.state(Status.IN_PROGRESS, claim.retryAfterSeconds());
            case CONFLICT: return Result.state(Status.CONFLICT, 0);
            case CANCELLED: return Result.state(Status.CANCELLED, 0);
            case CLAIMED: break;
        }
        CancellationSignal cancellation = activeTurns.register(command.getRequestId());
        try {
            Execution execution = executeClaimed(
                    conversationId, resumeTokenHash, command, cancellation);
            try {
                boolean completed = store.complete(
                        command.getRequestId(), fingerprint, execution.settledTurn(),
                        execution.contexts(), execution.challenges(), clock.instant());
                if (!completed) return Result.state(Status.CANCELLED, 0);
                return new Result(Status.COMPLETED, execution.settledTurn(), 0, false);
            } catch (RuntimeException settlementFailure) {
                return new Result(Status.COMPLETED, execution.readOnlyTurn(), 0, true);
            }
        } finally {
            activeTurns.remove(command.getRequestId(), cancellation);
        }
    }

    public boolean cancel(String conversationId, UUID requestId) {
        activeTurns.cancel(requestId);
        try {
            return store.cancel(requestId, conversationId, clock.instant());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private Execution executeClaimed(
            String conversationId, byte[] resumeTokenHash,
            AgentTurnCommand command, CancellationSignal cancellation) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ResolvedGoalSet resolved = goalResolver.resolve(command, resolutionContext(content));
        return switch (resolved.getKind()) {
            case CONVERSATIONAL -> simple(new PublicAgentTurn.Conversational(
                    command.getRequestId(), resolved.getMessage().orElseThrow(), List.of()));
            case BOUNDARY, INVALID_INPUT -> simple(new PublicAgentTurn.Boundary(
                    command.getRequestId(), resolved.getKind() == ResolvedGoalSet.Kind.INVALID_INPUT
                    ? "PUBLIC_SUBJECT_INVALID" : "OUT_OF_SCOPE",
                    resolved.getMessage().orElseThrow(), List.of()));
            case CAPABILITY_UNAVAILABLE -> simple(new PublicAgentTurn.CapabilityUnavailable(
                    command.getRequestId(), "GOAL_INTERPRETATION_UNAVAILABLE",
                    resolved.getMessage().orElseThrow(), true, List.of()));
            case CLARIFICATION -> clarification(
                    conversationId, resumeTokenHash, command.getRequestId(),
                    content.getContentVersion(), resolved.getClarification().orElseThrow());
            case GOALS -> goals(
                    conversationId, resumeTokenHash, command, cancellation, content,
                    planCompiler.compile(
                            resolved.getGoalProposal().orElseThrow(), content.getContentVersion(),
                            resolutionContext(content)));
        };
    }

    private Execution goals(
            String conversationId, byte[] resumeTokenHash, AgentTurnCommand command,
            CancellationSignal cancellation, RuntimeAnswerContent content,
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
                cancellation, List.of(), command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.Preset);
        List<ContextMutationPlanner.Mutation> mutations = mutationPlanner.plan(
                conversationId, plan, outcome, clock.instant().plus(contextTtl), Map.of());
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
    public record Result(Status status, PublicAgentTurn turn, long retryAfterSeconds,
                         boolean settlementFailed) {
        static Result state(Status status, long retryAfter) {
            return new Result(status, null, retryAfter, false);
        }
    }
    public enum Status { COMPLETED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED, STORE_UNAVAILABLE }
}
