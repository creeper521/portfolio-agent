package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.Objects;

public final class GoalResolver {
    private final GoalInterpretationPort interpretationPort;
    private final ReviewedGoalSource reviewedGoalSource;
    private final GoalInterpretationInputFactory inputFactory;
    private final SafeConversationalFastPath conversationalFastPath;
    private final SemanticRouteValidator routeValidator;
    private final GoalBoundaryPolicy boundaryPolicy;

    public GoalResolver(
            GoalInterpretationPort interpretationPort,
            ReviewedGoalSource reviewedGoalSource,
            GoalInterpretationInputFactory inputFactory,
            SafeConversationalFastPath conversationalFastPath,
            SemanticRouteValidator routeValidator,
            GoalBoundaryPolicy boundaryPolicy) {
        this.interpretationPort = Objects.requireNonNull(
                interpretationPort, "interpretationPort");
        this.reviewedGoalSource = Objects.requireNonNull(
                reviewedGoalSource, "reviewedGoalSource");
        this.inputFactory = Objects.requireNonNull(
                inputFactory, "inputFactory");
        this.conversationalFastPath = Objects.requireNonNull(
                conversationalFastPath, "conversationalFastPath");
        this.routeValidator = Objects.requireNonNull(
                routeValidator, "routeValidator");
        this.boundaryPolicy = Objects.requireNonNull(
                boundaryPolicy, "boundaryPolicy");
    }

    public ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline) {
        return resolve(command, context, deadline, null);
    }

    public ResolvedGoalSet resolve(
            AgentTurnCommand command,
            GoalResolutionContext context,
            TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(deadline, "deadline");
        if (!context.matchesHint(
                command.getSurfaceContext().getSubjectHint())) {
            return ResolvedGoalSet.invalidInput(
                    "指定的公开主体不存在或不可用。");
        }
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.FreeText) {
            return resolveFreeText(ask, context, deadline, recentSemanticState);
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
            TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        java.util.Optional<ResolvedGoalSet> conversational =
                conversationalFastPath.tryResolve(command);
        if (conversational.isPresent()) {
            return conversational.orElseThrow();
        }
        GoalInterpretationInput input = inputFactory.create(
                command, context, recentSemanticState);
        try {
            GoalInterpretationResult result =
                    interpretTyped(input, deadline);
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

    public GoalInterpretationResult interpretTyped(
            GoalInterpretationInput input,
            TurnDeadline deadline) {
        GoalInterpretationResult result =
                interpretationPort.interpret(input, deadline);
        if (result.getKind()
                == GoalInterpretationResult.Kind.CONVERSATIONAL) {
            return result;
        }
        return GoalInterpretationResult.semanticRoute(
                routeValidator.validate(
                        result.getRouteProposal().orElseThrow(), input));
    }

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
