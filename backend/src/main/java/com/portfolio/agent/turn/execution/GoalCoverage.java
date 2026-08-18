package com.portfolio.agent.turn.execution;

import java.util.Objects;

public final class GoalCoverage {
    private final String goalId;
    private final Coverage coverage;

    public GoalCoverage(String goalId, Coverage coverage) {
        if (goalId == null || goalId.isBlank()) throw new IllegalArgumentException("goalId is required");
        this.goalId = goalId;
        this.coverage = Objects.requireNonNull(coverage, "coverage");
    }

    public String getGoalId() { return goalId; }
    public Coverage getCoverage() { return coverage; }
    public enum Coverage { FULL, PARTIAL, NONE }
}
