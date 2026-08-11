package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.ExecutionSelection;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Executes a trusted semantic plan deterministically without creating tasks or planning tools. */
public final class SemanticTurnCoordinator {

    private final Map<TaskSourceDomain, SemanticTaskExecutor> executorsBySourceDomain;

    public SemanticTurnCoordinator(List<SemanticTaskExecutor> executors) {
        this.executorsBySourceDomain = indexExecutors(executors);
    }

    public SemanticTurnOutcome execute(
            ValidatedSemanticTurnPlan plan, ExecutionSelection selection) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(selection, "selection");
        validateSelection(plan, selection);

        List<SemanticTask> orderedTasks = stableTopologicalOrder(plan);
        Map<String, List<TaskDependency>> inboundDependencies = indexInboundDependencies(plan);
        Map<String, TaskOutcome> outcomesByTaskId = new LinkedHashMap<>();

        for (SemanticTask task : orderedTasks) {
            TaskOutcome selectedOutcome = selectionOutcome(task, selection);
            if (selectedOutcome != null) {
                outcomesByTaskId.put(task.getTaskId(), selectedOutcome);
                continue;
            }
            DependencyDecision dependency = assessDependencies(
                    task, inboundDependencies.getOrDefault(task.getTaskId(), List.of()), outcomesByTaskId);
            if (dependency.isBlocked()) {
                outcomesByTaskId.put(task.getTaskId(), TaskOutcome.blocked(
                        task.getTaskId(), task.getSourceDomain(), dependency.getReasonCode()));
                continue;
            }
            outcomesByTaskId.put(task.getTaskId(), executeSafely(task, dependency.getAvailableOutcomes()));
        }

        List<TaskOutcome> orderedOutcomes = new ArrayList<>();
        for (SemanticTask task : orderedTasks) {
            orderedOutcomes.add(outcomesByTaskId.get(task.getTaskId()));
        }
        return new SemanticTurnOutcome(orderedOutcomes);
    }

    private TaskOutcome selectionOutcome(SemanticTask task, ExecutionSelection selection) {
        String taskId = task.getTaskId();
        if (selection.isBlocked(taskId)) {
            return TaskOutcome.blocked(taskId, task.getSourceDomain(),
                    selection.getReasonCode(taskId).orElse("ROUTING_BLOCKED"));
        }
        if (selection.isDeferred(taskId)) {
            return TaskOutcome.cancelled(taskId, task.getSourceDomain(),
                    selection.getReasonCode(taskId).orElse("ROUTING_DEFERRED"));
        }
        if (!selection.isExecutable(taskId)) {
            return TaskOutcome.cancelled(taskId, task.getSourceDomain(), "ROUTING_NOT_SELECTED");
        }
        return null;
    }

    private DependencyDecision assessDependencies(
            SemanticTask task,
            List<TaskDependency> dependencies,
            Map<String, TaskOutcome> outcomesByTaskId) {
        List<TaskOutcome> availableOutcomes = new ArrayList<>();
        boolean hasAvailableRequiredInput = false;
        boolean hasAvailableResultsDependency = false;
        for (TaskDependency dependency : dependencies) {
            TaskOutcome upstream = outcomesByTaskId.get(dependency.getFromTaskId());
            if (upstream == null) {
                return DependencyDecision.blocked("EXECUTION_DEPENDENCY_UNAVAILABLE");
            }
            if (dependency.getType() == TaskDependencyType.REQUIRES_SUCCESS && !upstream.hasRenderablePayload()) {
                return DependencyDecision.blocked("EXECUTION_DEPENDENCY_BLOCKED");
            }
            if (dependency.getType() == TaskDependencyType.USES_AVAILABLE_RESULTS) {
                hasAvailableResultsDependency = true;
                if (upstream.hasRenderablePayload()) {
                    hasAvailableRequiredInput = true;
                    availableOutcomes.add(upstream);
                }
                continue;
            }
            if (dependency.getType() != TaskDependencyType.ORDER_AFTER && upstream.hasRenderablePayload()) {
                availableOutcomes.add(upstream);
            }
        }
        if (hasAvailableResultsDependency && !hasAvailableRequiredInput) {
            return DependencyDecision.blocked("EXECUTION_DEPENDENCY_UNAVAILABLE");
        }
        return DependencyDecision.executable(availableOutcomes);
    }

    private TaskOutcome executeSafely(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
        SemanticTaskExecutor executor = executorsBySourceDomain.get(task.getSourceDomain());
        if (executor == null) {
            return TaskOutcome.capabilityUnavailable(
                    task.getTaskId(), task.getSourceDomain(), "CAPABILITY_EXECUTOR_UNAVAILABLE");
        }
        try {
            TaskOutcome outcome = executor.execute(task, List.copyOf(availableDependencyOutcomes));
            if (outcome == null
                    || !task.getTaskId().equals(outcome.getTaskId())
                    || task.getSourceDomain() != outcome.getSourceDomain()) {
                return TaskOutcome.failed(
                        task.getTaskId(), task.getSourceDomain(), "EXECUTION_INVALID_OUTCOME");
            }
            return outcome;
        } catch (RuntimeException exception) {
            return TaskOutcome.failed(task.getTaskId(), task.getSourceDomain(), "EXECUTION_UNEXPECTED_FAILURE");
        }
    }

    private List<SemanticTask> stableTopologicalOrder(ValidatedSemanticTurnPlan plan) {
        Map<String, SemanticTask> tasksById = new LinkedHashMap<>();
        Map<String, Integer> taskOrderById = new LinkedHashMap<>();
        Map<String, Integer> indegreeByTaskId = new LinkedHashMap<>();
        Map<String, List<String>> outgoingByTaskId = new LinkedHashMap<>();
        int taskOrder = 0;
        for (SemanticTask task : plan.getTasks()) {
            tasksById.put(task.getTaskId(), task);
            taskOrderById.put(task.getTaskId(), taskOrder++);
            indegreeByTaskId.put(task.getTaskId(), 0);
            outgoingByTaskId.put(task.getTaskId(), new ArrayList<>());
        }
        for (TaskDependency dependency : plan.getDependencies()) {
            outgoingByTaskId.get(dependency.getFromTaskId()).add(dependency.getToTaskId());
            indegreeByTaskId.put(dependency.getToTaskId(), indegreeByTaskId.get(dependency.getToTaskId()) + 1);
        }

        PriorityQueue<String> readyTaskIds = new PriorityQueue<>(
                java.util.Comparator.comparingInt(taskOrderById::get));
        for (SemanticTask task : plan.getTasks()) {
            if (indegreeByTaskId.get(task.getTaskId()) == 0) {
                readyTaskIds.add(task.getTaskId());
            }
        }
        List<SemanticTask> ordered = new ArrayList<>();
        while (!readyTaskIds.isEmpty()) {
            String taskId = readyTaskIds.remove();
            ordered.add(tasksById.get(taskId));
            for (String downstreamTaskId : outgoingByTaskId.get(taskId)) {
                int remaining = indegreeByTaskId.get(downstreamTaskId) - 1;
                indegreeByTaskId.put(downstreamTaskId, remaining);
                if (remaining == 0) {
                    readyTaskIds.add(downstreamTaskId);
                }
            }
        }
        if (ordered.size() != plan.getTasks().size()) {
            throw new IllegalArgumentException("validated plan must be acyclic");
        }
        return List.copyOf(ordered);
    }

    private Map<String, List<TaskDependency>> indexInboundDependencies(ValidatedSemanticTurnPlan plan) {
        Map<String, List<TaskDependency>> inbound = new LinkedHashMap<>();
        for (TaskDependency dependency : plan.getDependencies()) {
            inbound.computeIfAbsent(dependency.getToTaskId(), ignored -> new ArrayList<>()).add(dependency);
        }
        Map<String, List<TaskDependency>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<TaskDependency>> entry : inbound.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private void validateSelection(ValidatedSemanticTurnPlan plan, ExecutionSelection selection) {
        Set<String> knownTaskIds = new HashSet<>();
        for (SemanticTask task : plan.getTasks()) {
            knownTaskIds.add(task.getTaskId());
        }
        Set<String> selectedTaskIds = new LinkedHashSet<>();
        selectedTaskIds.addAll(selection.getExecutableTaskIds());
        selectedTaskIds.addAll(selection.getDeferredTaskIds());
        selectedTaskIds.addAll(selection.getBlockedTaskIds());
        if (!knownTaskIds.containsAll(selectedTaskIds)) {
            throw new IllegalArgumentException("execution selection references a task outside the validated plan");
        }
        if (!selectedTaskIds.equals(knownTaskIds)) {
            throw new IllegalArgumentException("execution selection must cover every validated plan task");
        }
    }

    private static Map<TaskSourceDomain, SemanticTaskExecutor> indexExecutors(
            List<SemanticTaskExecutor> executors) {
        Objects.requireNonNull(executors, "executors");
        Map<TaskSourceDomain, SemanticTaskExecutor> indexed = new HashMap<>();
        for (SemanticTaskExecutor executor : executors) {
            SemanticTaskExecutor nonNullExecutor = Objects.requireNonNull(executor, "executor");
            TaskSourceDomain sourceDomain = Objects.requireNonNull(
                    nonNullExecutor.getSourceDomain(), "executor.sourceDomain");
            SemanticTaskExecutor existing = indexed.put(sourceDomain, nonNullExecutor);
            if (existing != null) {
                throw new IllegalArgumentException("only one executor may serve each source domain");
            }
        }
        return Map.copyOf(indexed);
    }

    private static final class DependencyDecision {

        private final String reasonCode;
        private final List<TaskOutcome> availableOutcomes;

        private DependencyDecision(String reasonCode, List<TaskOutcome> availableOutcomes) {
            this.reasonCode = reasonCode;
            this.availableOutcomes = List.copyOf(availableOutcomes);
        }

        private static DependencyDecision blocked(String reasonCode) {
            return new DependencyDecision(reasonCode, List.of());
        }

        private static DependencyDecision executable(List<TaskOutcome> availableOutcomes) {
            return new DependencyDecision(null, availableOutcomes);
        }

        private boolean isBlocked() {
            return reasonCode != null;
        }

        private String getReasonCode() {
            return reasonCode;
        }

        private List<TaskOutcome> getAvailableOutcomes() {
            return availableOutcomes;
        }
    }
}
