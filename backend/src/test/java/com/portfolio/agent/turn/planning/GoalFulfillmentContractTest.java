package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalFulfillmentContractTest {

    @Test
    void everyGoalHasExactlyOneExistingFulfillmentTask() {
        SemanticPlanCompiler compiler = new SemanticPlanCompiler(new SemanticPlanValidator());
        SemanticTurnPlan plan = compiler.compile(
                new UserGoalProposal(List.of(
                        SemanticPlanCompilerTest.portfolioFact(),
                        SemanticPlanCompilerTest.generalExplanation())),
                "2026-08-05.1", SemanticPlanCompilerTest.context())
                .getPlan().orElseThrow().getPlan();

        assertThat(plan.getUserGoals()).allSatisfy(goal ->
                assertThat(plan.findTask(goal.getFulfillmentTaskId())).isPresent());
        assertThat(plan.getUserGoals()).extracting(UserGoal::getFulfillmentTaskId)
                .doesNotHaveDuplicates();
    }
}
