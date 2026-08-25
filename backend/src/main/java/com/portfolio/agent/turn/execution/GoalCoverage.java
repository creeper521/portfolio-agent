package com.portfolio.agent.turn.execution;

import java.util.Objects;

/**
 * 单个 UserGoal 的执行后覆盖度结论，不可变值对象。
 *
 * <p>由 Engine 在 Turn 执行结束时经 {@link GoalCoverageProjector} 从任务终态
 * 投影产出，Capability 不直接声明覆盖度。
 */
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
    /** 覆盖等级：FULL 完全满足、PARTIAL 部分满足、NONE 未获得任何产出。 */
    public enum Coverage { FULL, PARTIAL, NONE }
}
