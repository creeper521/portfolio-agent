package com.portfolio.agent.answer.routing.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic whole-turn budget allocated before task execution starts. */
public final class SemanticTurnExecutionBudget {

    public static final int TOTAL_CHARACTER_LIMIT = 8000;

    private final Instant executionStartedAt;
    private final Instant absoluteDeadline;
    private final Map<String, TaskExecutionAllowance> allowancesByTaskId;

    public SemanticTurnExecutionBudget(
            Instant executionStartedAt,
            Instant absoluteDeadline,
            Map<String, TaskExecutionAllowance> allowancesByTaskId) {
        this.executionStartedAt = Objects.requireNonNull(executionStartedAt, "executionStartedAt");
        this.absoluteDeadline = Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        if (absoluteDeadline.isBefore(executionStartedAt)) {
            throw new IllegalArgumentException("absoluteDeadline must not precede executionStartedAt");
        }
        Objects.requireNonNull(allowancesByTaskId, "allowancesByTaskId");
        Map<String, TaskExecutionAllowance> copied = new LinkedHashMap<>();
        for (Map.Entry<String, TaskExecutionAllowance> entry : allowancesByTaskId.entrySet()) {
            copied.put(requireText(entry.getKey(), "taskId"),
                    Objects.requireNonNull(entry.getValue(), "allowance"));
        }
        this.allowancesByTaskId = Collections.unmodifiableMap(new LinkedHashMap<>(copied));
    }

    public static SemanticTurnExecutionBudget allocate(
            List<SemanticTask> orderedTasks, Set<String> executableTaskIds,
            Instant executionStartedAt, Instant absoluteDeadline) {
        Objects.requireNonNull(orderedTasks, "orderedTasks");
        Objects.requireNonNull(executableTaskIds, "executableTaskIds");
        int executableCount = executableTaskIds.size();
        int base = executableCount == 0 ? 0 : Math.min(4000, TOTAL_CHARACTER_LIMIT / executableCount);
        int remainder = executableCount == 0 ? 0 : TOTAL_CHARACTER_LIMIT - base * executableCount;
        Map<String, TaskExecutionAllowance> allowances = new LinkedHashMap<>();
        for (SemanticTask task : orderedTasks) {
            if (!executableTaskIds.contains(task.getTaskId())) {
                continue;
            }
            int characters = base;
            if (remainder > 0 && characters < 4000) {
                characters++;
                remainder--;
            }
            allowances.put(task.getTaskId(), TaskExecutionAllowance.forTask(
                    task.getTaskType(), characters, absoluteDeadline));
        }
        return new SemanticTurnExecutionBudget(
                executionStartedAt, absoluteDeadline, allowances);
    }

    public static SemanticTurnExecutionBudget forExecutableTaskCount(
            int executableTaskCount, Instant executionStartedAt, Instant absoluteDeadline) {
        if (executableTaskCount < 0) {
            throw new IllegalArgumentException("executableTaskCount must not be negative");
        }
        List<SemanticTask> placeholders = new ArrayList<>();
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int index = 0; index < executableTaskCount; index++) {
            String id = "task-" + index;
            ids.add(id);
            placeholders.add(SemanticTask.create(
                    id,
                    com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                    com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                    "budget",
                    new SemanticTaskParameters.PortfolioFact(
                            SubjectReference.project("budget-" + index, "budget"),
                            Set.of("OVERVIEW"), "GUEST"),
                    Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                    TaskConfidence.highRule(),
                    List.of(SubjectReference.project("budget-" + index, "budget"))));
        }
        return allocate(placeholders, ids, executionStartedAt, absoluteDeadline);
    }

    public TaskExecutionAllowance getAllowance(String taskId) {
        TaskExecutionAllowance allowance = allowancesByTaskId.get(taskId);
        if (allowance == null) {
            throw new IllegalArgumentException("no allowance exists for task");
        }
        return allowance;
    }

    public boolean containsTask(String taskId) {
        return allowancesByTaskId.containsKey(taskId);
    }

    public Instant getExecutionStartedAt() {
        return executionStartedAt;
    }

    public Instant getAbsoluteDeadline() {
        return absoluteDeadline;
    }

    public Map<String, TaskExecutionAllowance> getAllowancesByTaskId() {
        return allowancesByTaskId;
    }

    @Override
    public String toString() {
        return "SemanticTurnExecutionBudget{taskCount=" + allowancesByTaskId.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
