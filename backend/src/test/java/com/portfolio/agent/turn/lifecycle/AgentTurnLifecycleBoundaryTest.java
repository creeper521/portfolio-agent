package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.SafeConversationalFastPath;
import com.portfolio.agent.turn.planning.SemanticRouteValidator;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentTurnLifecycleBoundaryTest {

    @Test
    void reviewedComparisonTraversesResolverAndSettlesBeforeEveryProvider()
            throws Exception {
        AtomicInteger goalProviderCalls = new AtomicInteger();
        UserGoalProposal comparison = comparisonProposal();
        GoalResolver resolver = new GoalResolver(
                (input, deadline, execution) -> {
                    goalProviderCalls.incrementAndGet();
                    throw new AssertionError(
                            "reviewed Comparison must not call Goal Provider");
                },
                ignored -> comparison,
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(),
                new SemanticRouteValidator(), new GoalBoundaryPolicy());
        SemanticPlanCompiler compiler = mock(SemanticPlanCompiler.class);
        SemanticTurnEngine engine = mock(SemanticTurnEngine.class);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                new InMemoryTurnExecutionStore(), resolver, compiler, engine);

        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Ask(
                        UUID.randomUUID(),
                        AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.Preset(
                                "compare-concepts",
                                "pcv1-0123456789abcdef"),
                        null, null));

        assertThat(result.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.Boundary.class);
        assertThat(((PublicAgentTurn.Boundary) result.turn()).getMessage())
                .isEqualTo("当前暂不支持直接比较；请分别询问这些概念。");
        assertThat(goalProviderCalls).hasValue(0);
        verifyNoInteractions(compiler, engine);
    }

    @Test
    void boundarySettlesWithoutCompilingOrExecutingAGeneralPlan() {
        SemanticPlanCompiler compiler = mock(SemanticPlanCompiler.class);
        SemanticTurnEngine engine = mock(SemanticTurnEngine.class);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                new InMemoryTurnExecutionStore(),
                ResolvedGoalSet.boundary(
                        "\u5f53\u524d\u6682\u4e0d\u652f\u6301\u76f4\u63a5\u6bd4\u8f83\uff1b\u8bf7\u5206\u522b\u8be2\u95ee\u8fd9\u4e9b\u6982\u5ff5\u3002"),
                compiler, engine);
        UUID requestId = UUID.randomUUID();

        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Ask(
                        requestId, AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("\u6bd4\u8f83\u591a\u4e2a\u4e3b\u4f53\u7684\u591a\u4e2a\u7ef4\u5ea6"),
                        null, null));

        assertThat(result.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.Boundary.class);
        PublicAgentTurn.Boundary boundary =
                (PublicAgentTurn.Boundary) result.turn();
        assertThat(boundary.getRequestId()).isEqualTo(requestId);
        assertThat(boundary.getCode()).isEqualTo("OUT_OF_SCOPE");
        assertThat(boundary.getMessage()).isEqualTo(
                "\u5f53\u524d\u6682\u4e0d\u652f\u6301\u76f4\u63a5\u6bd4\u8f83\uff1b\u8bf7\u5206\u522b\u8be2\u95ee\u8fd9\u4e9b\u6982\u5ff5\u3002");
        verifyNoInteractions(compiler, engine);
    }

    private UserGoalProposal comparisonProposal() {
        return new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "general-comparison", GoalKind.GENERAL_COMPARISON,
                        new UserGoalProposal.InputAnchor("比较", 0),
                        List.of(), Set.of(GoalRequestedOutput.COMPARISON),
                        GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION,
                        new UserGoalProposal.GeneralComparisonParameters(
                                List.of(
                                        new UserGoalProposal.InputAnchor(
                                                "锁", 0),
                                        new UserGoalProposal.InputAnchor(
                                                "事务", 1)),
                                Set.of("MECHANISM")))));
    }
}
