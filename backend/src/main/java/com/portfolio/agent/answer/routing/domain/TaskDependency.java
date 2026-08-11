package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;

import java.util.Objects;

public final class TaskDependency {

    private final String fromTaskId;
    private final String toTaskId;
    private final TaskDependencyType type;
    private final DependencyOrigin origin;

    public TaskDependency(
            String fromTaskId, String toTaskId, TaskDependencyType type, DependencyOrigin origin) {
        this.fromTaskId = requireText(fromTaskId, "fromTaskId");
        this.toTaskId = requireText(toTaskId, "toTaskId");
        if (this.fromTaskId.equals(this.toTaskId)) {
            throw new IllegalArgumentException("dependency must not be a self reference");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public String getFromTaskId() {
        return fromTaskId;
    }

    public String getToTaskId() {
        return toTaskId;
    }

    public TaskDependencyType getType() {
        return type;
    }

    public DependencyOrigin getOrigin() {
        return origin;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskDependency that)) {
            return false;
        }
        return Objects.equals(fromTaskId, that.fromTaskId)
                && Objects.equals(toTaskId, that.toTaskId)
                && type == that.type
                && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTaskId, toTaskId, type, origin);
    }

    @Override
    public String toString() {
        return "TaskDependency{type=" + type + ", origin=" + origin + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
