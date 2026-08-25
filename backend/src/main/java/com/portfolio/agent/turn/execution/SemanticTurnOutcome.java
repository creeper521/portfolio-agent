package com.portfolio.agent.turn.execution;

import java.util.List;
import java.util.Objects;

/**
 * 一轮 Turn 的执行结果聚合，不可变：按计划顺序排列的全部任务终态，
 * 外加经 {@link GoalCoverageProjector} 投影的每个 UserGoal 覆盖度结论。
 * 产出后视图即冻结，是 Settlement 阶段的输入。
 */
public final class SemanticTurnOutcome {
    private final List<TaskOutcome> taskOutcomes;
    private final List<GoalCoverage> goalCoverage;

    public SemanticTurnOutcome(List<TaskOutcome> taskOutcomes, List<GoalCoverage> goalCoverage) {
        this.taskOutcomes = List.copyOf(Objects.requireNonNull(taskOutcomes, "taskOutcomes"));
        this.goalCoverage = List.copyOf(Objects.requireNonNull(goalCoverage, "goalCoverage"));
    }

    public List<TaskOutcome> getTaskOutcomes() { return taskOutcomes; }
    public List<GoalCoverage> getGoalCoverage() { return goalCoverage; }
}
