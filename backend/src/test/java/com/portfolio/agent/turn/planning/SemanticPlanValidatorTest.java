package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticPlanValidatorTest {

    private final SemanticPlanValidator validator = new SemanticPlanValidator();

    @Test
    void rejectsMissingFulfillmentTaskAndCycles() {
        UserGoal goal = new UserGoal(
                "goal-1", "目标", GoalKind.GENERAL_EXPLANATION,
                List.of(), java.util.Set.of(GoalRequestedOutput.EXPLANATION), "missing");
        SemanticTask first = SemanticTask.of(
                "task-a", SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(GoalKind.GENERAL_EXPLANATION,
                        SemanticPlanCompilerTest.generalExplanation().getParameters(), List.of()));
        SemanticTask second = SemanticTask.of(
                "task-b", SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(GoalKind.GENERAL_EXPLANATION,
                        SemanticPlanCompilerTest.generalExplanation().getParameters(), List.of()));
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "2026-08-05.1", List.of(goal), List.of(first, second),
                List.of(new TaskDependency("task-a", "task-b"),
                        new TaskDependency("task-b", "task-a")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
