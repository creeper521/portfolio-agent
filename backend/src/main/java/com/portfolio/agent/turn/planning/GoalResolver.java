package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.util.Objects;

public final class GoalResolver {
    private final GoalInterpretationPort interpretationPort;
    private final ReviewedGoalSource reviewedGoalSource;
    private final GoalInterpretationInputFactory inputFactory;
    private final MinimalGoalFallback minimalFallback;
    private final GoalBoundaryPolicy boundaryPolicy;

    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            MinimalGoalFallback minimalFallback,
            GoalBoundaryPolicy boundaryPolicy) {
        this.interpretationPort = Objects.requireNonNull(interpretationPort, "interpretationPort");
        this.reviewedGoalSource = Objects.requireNonNull(reviewedGoalSource, "reviewedGoalSource");
        this.inputFactory = Objects.requireNonNull(inputFactory, "inputFactory");
        this.minimalFallback = Objects.requireNonNull(minimalFallback, "minimalFallback");
        this.boundaryPolicy = Objects.requireNonNull(boundaryPolicy, "boundaryPolicy");
    }

    public ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(deadline, "deadline");
        if (!context.matchesHint(command.getSurfaceContext().getSubjectHint())) {
            return ResolvedGoalSet.invalidInput("指定的公开主体不存在或不可用。");
        }
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.FreeText) {
            return resolveFreeText(ask, context, deadline);
        }
        try {
            return boundaryPolicy.apply(reviewedGoalSource.resolve(command));
        } catch (ReviewedGoalUnavailableException unavailable) {
            return ResolvedGoalSet.capabilityUnavailable(
                    "当前续接或澄清状态不可用，请重新提问。");
        }
    }

    private ResolvedGoalSet resolveFreeText(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context,
            TurnDeadline deadline) {
        java.util.Optional<ResolvedGoalSet> deterministic =
                minimalFallback.tryResolveBeforeProvider(command, context);
        if (deterministic.isPresent()) return deterministic.orElseThrow();
        try {
            GoalInterpretationResult result = interpretationPort.interpret(
                    inputFactory.create(command, context), deadline);
            return switch (result.getKind()) {
                case GOALS -> boundaryPolicy.apply(result.getGoalProposal().orElseThrow());
                case CLARIFICATION -> ResolvedGoalSet.clarification(
                        result.getClarification().orElseThrow());
                case CONVERSATIONAL -> ResolvedGoalSet.conversational(
                        result.getMessage().orElseThrow());
            };
        } catch (GoalInterpretationUnavailableException unavailable) {
            if (deadline.isExpired()) {
                return ResolvedGoalSet.capabilityUnavailable(
                        "目标解释已超过本轮预算，请重新提问。");
            }
            return minimalFallback.tryResolve(command, context)
                    .map(boundaryPolicy::apply)
                    .orElseGet(() -> ResolvedGoalSet.capabilityUnavailable(
                            "当前暂时无法可靠理解这条自由文本请求。"));
        }
    }
}
