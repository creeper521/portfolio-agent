package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicConversationBoundaryTest {

    @Test
    void greetingStaysLocalWhileRecommendationUsesTheProvider() {
        AtomicInteger providerCalls = new AtomicInteger();
        UserGoalProposal recommendationProposal = recommendationProposal();
        GoalResolver resolver = new GoalResolver(
                (input, deadline) -> {
                    providerCalls.incrementAndGet();
                    return GoalInterpretationResult.semanticRoute(
                            SemanticRouteProposal.standardGoal(
                                    recommendationProposal));
                }, command -> { throw new AssertionError("reviewed source must not be called"); },
                new GoalInterpretationInputFactory(), new SafeConversationalFastPath(),
                new SemanticRouteValidator(),
                new GoalBoundaryPolicy());
        GoalResolutionContext context = new GoalResolutionContext(List.of(), Set.of(GoalKind.values()));

        ResolvedGoalSet greeting = resolver.resolve(ask("您好"), context, deadline());
        ResolvedGoalSet recommendation = resolver.resolve(
                ask("请推荐两个后端项目"), context, deadline());

        assertThat(greeting.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        UserGoalProposal.ProposedGoal goal = recommendation.getGoalProposal()
                .orElseThrow().getGoals().getFirst();
        assertThat(goal.getGoalKind()).isEqualTo(GoalKind.PORTFOLIO_RECOMMEND);
        assertThat(((UserGoalProposal.PortfolioRecommendationParameters)
                goal.getParameters()).getRequestedSize()).isEqualTo(2);
        assertThat(providerCalls).hasValue(1);
    }

    private UserGoalProposal recommendationProposal() {
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("请推荐两个后端项目", 0);
        return new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "portfolio-recommendation",
                        GoalKind.PORTFOLIO_RECOMMEND,
                        anchor,
                        List.of(),
                        Set.of(GoalRequestedOutput.RECOMMENDATION),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioRecommendationParameters(
                                2, Set.of("BACKEND")))));
    }

    private AgentTurnCommand ask(String text) {
        return new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText(text),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(Duration.ofSeconds(1), java.time.Clock.systemUTC());
    }
}
