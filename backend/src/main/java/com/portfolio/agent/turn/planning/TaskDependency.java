package com.portfolio.agent.turn.planning;

public final class TaskDependency {
    private final String fromTaskId;
    private final String toTaskId;

    public TaskDependency(String fromTaskId, String toTaskId) {
        this.fromTaskId = UserGoal.requireId(fromTaskId, "fromTaskId");
        this.toTaskId = UserGoal.requireId(toTaskId, "toTaskId");
        if (this.fromTaskId.equals(this.toTaskId)) {
            throw new IllegalArgumentException("dependency cannot reference itself");
        }
    }

    public String getFromTaskId() { return fromTaskId; }
    public String getToTaskId() { return toTaskId; }
}
