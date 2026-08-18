package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTopologyContractTest {

    @Test
    void compilerProducesOnlyDataEdgesForRealFanIn() {
        SemanticPlanCompiler compiler = new SemanticPlanCompiler(new SemanticPlanValidator());
        SemanticTurnPlan plan = compiler.compile(
                new UserGoalProposal(List.of(SemanticPlanCompilerTest.crossDomain())),
                "2026-08-05.1", SemanticPlanCompilerTest.context())
                .getPlan().orElseThrow().getPlan();

        assertThat(TaskDependency.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("fromTaskId", "toTaskId");
        assertThat(plan.getDependencies()).allSatisfy(edge ->
                assertThat(edge.getToTaskId()).isEqualTo("task-goal-1"));
    }
}
