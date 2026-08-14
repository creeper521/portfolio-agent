package com.portfolio.agent.answer.routing.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Plan-level immutable outcome in the plan's stable task order. */
public final class SemanticTurnOutcome {

    public enum PlanOutcome {
        SUCCEEDED,
        PARTIAL,
        NO_RESULT,
        FAILED,
        CANCELLED
    }

    private final PlanOutcome planOutcome;
    private final List<TaskOutcome> taskOutcomes;
    private final Map<String, TaskOutcome> outcomesByTaskId;
    private final boolean degraded;

    public SemanticTurnOutcome(List<TaskOutcome> taskOutcomes) {
        this.taskOutcomes = copyOutcomes(taskOutcomes);
        this.outcomesByTaskId = indexByTaskId(this.taskOutcomes);
        this.planOutcome = derivePlanOutcome(this.taskOutcomes);
        this.degraded = this.taskOutcomes.stream().anyMatch(TaskOutcome::isDegraded);
    }

    public PlanOutcome getPlanOutcome() {
        return planOutcome;
    }

    public List<TaskOutcome> getTaskOutcomes() {
        return taskOutcomes;
    }

    public Optional<TaskOutcome> getTask(String taskId) {
        return Optional.ofNullable(outcomesByTaskId.get(taskId));
    }

    public boolean isDegraded() {
        return degraded;
    }

    public int getAnsweredCount() {
        return countResolution(TaskOutcome.TaskResolution.ANSWERED)
                + countResolution(TaskOutcome.TaskResolution.PARTIALLY_ANSWERED);
    }

    public int getNotSupportedCount() {
        return countResolution(TaskOutcome.TaskResolution.NOT_SUPPORTED)
                + countResolution(TaskOutcome.TaskResolution.CAPABILITY_UNAVAILABLE);
    }

    public int getEmptyCount() {
        return countResolution(TaskOutcome.TaskResolution.EMPTY);
    }

    public int getBlockedCount() {
        return countStatus(TaskOutcome.TaskExecutionStatus.BLOCKED);
    }

    public int getFailedCount() {
        return countStatus(TaskOutcome.TaskExecutionStatus.FAILED);
    }

    public int getCancelledCount() {
        return countStatus(TaskOutcome.TaskExecutionStatus.CANCELLED);
    }

    public int getDegradedCount() {
        int count = 0;
        for (TaskOutcome outcome : taskOutcomes) {
            if (outcome.isDegraded()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticTurnOutcome that)) {
            return false;
        }
        return degraded == that.degraded
                && planOutcome == that.planOutcome
                && Objects.equals(taskOutcomes, that.taskOutcomes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planOutcome, taskOutcomes, degraded);
    }

    @Override
    public String toString() {
        return "SemanticTurnOutcome{planOutcome=" + planOutcome
                + ", taskCount=" + taskOutcomes.size()
                + ", answeredCount=" + getAnsweredCount()
                + ", blockedCount=" + getBlockedCount()
                + ", failedCount=" + getFailedCount()
                + ", degradedCount=" + getDegradedCount() + '}';
    }

    private int countResolution(TaskOutcome.TaskResolution resolution) {
        int count = 0;
        for (TaskOutcome outcome : taskOutcomes) {
            if (outcome.getResolution() == resolution) {
                count++;
            }
        }
        return count;
    }

    private int countStatus(TaskOutcome.TaskExecutionStatus status) {
        int count = 0;
        for (TaskOutcome outcome : taskOutcomes) {
            if (outcome.getExecutionStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private static List<TaskOutcome> copyOutcomes(List<TaskOutcome> taskOutcomes) {
        Objects.requireNonNull(taskOutcomes, "taskOutcomes");
        if (taskOutcomes.isEmpty()) {
            throw new IllegalArgumentException("taskOutcomes must not be empty");
        }
        return List.copyOf(taskOutcomes);
    }

    private static Map<String, TaskOutcome> indexByTaskId(List<TaskOutcome> outcomes) {
        Map<String, TaskOutcome> indexed = new LinkedHashMap<>();
        for (TaskOutcome outcome : outcomes) {
            TaskOutcome existing = indexed.put(outcome.getTaskId(), outcome);
            if (existing != null) {
                throw new IllegalArgumentException("task outcomes must have distinct task ids");
            }
        }
        return Map.copyOf(indexed);
    }

    private static PlanOutcome derivePlanOutcome(List<TaskOutcome> outcomes) {
        List<TaskOutcome> primaryOutcomes = outcomes.stream()
                .filter(outcome -> outcome.getFulfillmentRole() == TaskFulfillmentRole.PRIMARY)
                .toList();
        List<TaskOutcome> considered = primaryOutcomes.isEmpty() ? outcomes : primaryOutcomes;
        int answeredCount = 0;
        int failedCount = 0;
        int cancelledCount = 0;
        for (TaskOutcome outcome : considered) {
            if (outcome.getResolution() == TaskOutcome.TaskResolution.ANSWERED
                    || outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED) {
                answeredCount++;
            }
            if (outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.FAILED) {
                failedCount++;
            }
            if (outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.CANCELLED) {
                cancelledCount++;
            }
        }
        if (answeredCount == considered.size()) {
            return PlanOutcome.SUCCEEDED;
        }
        if (answeredCount > 0) {
            return PlanOutcome.PARTIAL;
        }
        if (cancelledCount == considered.size()) {
            return PlanOutcome.CANCELLED;
        }
        if (failedCount > 0) {
            return PlanOutcome.FAILED;
        }
        return PlanOutcome.NO_RESULT;
    }
}
