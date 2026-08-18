package com.portfolio.agent.turn.execution;

import java.util.List;
import java.util.Objects;

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
