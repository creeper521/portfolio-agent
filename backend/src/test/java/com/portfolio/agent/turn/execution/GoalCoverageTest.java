package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

class GoalCoverageTest {
    @Test
    void coverageHasOnlyFullPartialAndNone() {
        assertThat(GoalCoverage.Coverage.values())
                .containsExactly(
                        GoalCoverage.Coverage.FULL,
                        GoalCoverage.Coverage.PARTIAL,
                        GoalCoverage.Coverage.NONE);
    }

    @Test
    void fulfillmentTerminalIsTheOnlyCoverageAuthority() {
        com.portfolio.agent.turn.planning.SemanticTurnPlan plan =
                ExecutionTestPlanFactory.oneGeneralTask().getPlan();
        String taskId = plan.getTasks().getFirst().getTaskId();
        TaskOutcome partial = new TaskOutcome(taskId, new TaskOutcome.Produced(
                ExecutionTestPlanFactory.artifact(), TaskOutcome.Fulfillment.PARTIAL));

        assertThat(new GoalCoverageProjector().project(plan, Map.of(taskId, partial)))
                .extracting(GoalCoverage::getCoverage)
                .containsExactly(GoalCoverage.Coverage.PARTIAL);
        assertThat(new GoalCoverageProjector().project(plan, Map.of()))
                .extracting(GoalCoverage::getCoverage)
                .containsExactly(GoalCoverage.Coverage.NONE);
    }
}
