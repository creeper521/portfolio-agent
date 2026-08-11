package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.List;
import java.util.Objects;

/** User-facing plan only: stable display order, goal, source, and natural-language dependencies. */
public final class DisplayPlanResponse {

    private final int taskCount;
    private final Integer executableTaskCount;
    private final List<Task> tasks;
    private final List<String> constraints;

    public DisplayPlanResponse(
            int taskCount,
            Integer executableTaskCount,
            List<Task> tasks,
            List<String> constraints) {
        if (taskCount < 0) {
            throw new IllegalArgumentException("taskCount must not be negative");
        }
        this.taskCount = taskCount;
        this.executableTaskCount = executableTaskCount;
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        this.constraints = constraints == null ? null : List.copyOf(constraints);
    }

    public int getTaskCount() { return taskCount; }
    public Integer getExecutableTaskCount() { return executableTaskCount; }
    public List<Task> getTasks() { return tasks; }
    public List<String> getConstraints() { return constraints; }

    public static final class Task {
        private final String displayIndex;
        private final String goalLabel;
        private final TaskSourceDomain sourceDomain;
        private final String dependencySummary;

        public Task(
                String displayIndex,
                String goalLabel,
                TaskSourceDomain sourceDomain,
                String dependencySummary) {
            this.displayIndex = requireText(displayIndex, "displayIndex");
            this.goalLabel = requireText(goalLabel, "goalLabel");
            this.sourceDomain = Objects.requireNonNull(sourceDomain, "sourceDomain");
            this.dependencySummary = normalize(dependencySummary);
        }

        public String getDisplayIndex() { return displayIndex; }
        public String getGoalLabel() { return goalLabel; }
        public TaskSourceDomain getSourceDomain() { return sourceDomain; }
        public String getDependencySummary() { return dependencySummary; }

        private static String requireText(String value, String name) {
            String normalized = normalize(value);
            if (normalized == null) {
                throw new IllegalArgumentException(name + " is required");
            }
            return normalized;
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
