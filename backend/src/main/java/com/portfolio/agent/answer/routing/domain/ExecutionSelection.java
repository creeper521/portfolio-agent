package com.portfolio.agent.answer.routing.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable routing decision about which validated tasks may execute this turn. */
public final class ExecutionSelection {

    private final Set<String> executableTaskIds;
    private final Set<String> deferredTaskIds;
    private final Set<String> blockedTaskIds;
    private final Map<String, String> reasonCodesByTask;

    private ExecutionSelection(
            Set<String> executableTaskIds,
            Set<String> deferredTaskIds,
            Set<String> blockedTaskIds,
            Map<String, String> reasonCodesByTask) {
        this.executableTaskIds = copyTaskIds(executableTaskIds, "executableTaskIds");
        this.deferredTaskIds = copyTaskIds(deferredTaskIds, "deferredTaskIds");
        this.blockedTaskIds = copyTaskIds(blockedTaskIds, "blockedTaskIds");
        this.reasonCodesByTask = copyReasons(reasonCodesByTask);
        validateDisjointPartitions();
        validateReasonTargets();
    }

    public static ExecutionSelection allExecutable(Set<String> executableTaskIds) {
        return new ExecutionSelection(executableTaskIds, Set.of(), Set.of(), Map.of());
    }

    public static ExecutionSelection partition(
            Set<String> executableTaskIds,
            Set<String> deferredTaskIds,
            Set<String> blockedTaskIds,
            Map<String, String> reasonCodesByTask) {
        return new ExecutionSelection(executableTaskIds, deferredTaskIds, blockedTaskIds, reasonCodesByTask);
    }

    public Set<String> getExecutableTaskIds() {
        return executableTaskIds;
    }

    public Set<String> getDeferredTaskIds() {
        return deferredTaskIds;
    }

    public Set<String> getBlockedTaskIds() {
        return blockedTaskIds;
    }

    public Map<String, String> getReasonCodesByTask() {
        return reasonCodesByTask;
    }

    public boolean isExecutable(String taskId) {
        return executableTaskIds.contains(taskId);
    }

    public boolean isDeferred(String taskId) {
        return deferredTaskIds.contains(taskId);
    }

    public boolean isBlocked(String taskId) {
        return blockedTaskIds.contains(taskId);
    }

    public Optional<String> getReasonCode(String taskId) {
        return Optional.ofNullable(reasonCodesByTask.get(taskId));
    }

    private void validateDisjointPartitions() {
        Set<String> allTaskIds = new LinkedHashSet<>();
        addPartition(allTaskIds, executableTaskIds, "execution partitions must be disjoint");
        addPartition(allTaskIds, deferredTaskIds, "execution partitions must be disjoint");
        addPartition(allTaskIds, blockedTaskIds, "execution partitions must be disjoint");
    }

    private void validateReasonTargets() {
        Set<String> selectedTaskIds = new LinkedHashSet<>();
        selectedTaskIds.addAll(deferredTaskIds);
        selectedTaskIds.addAll(blockedTaskIds);
        if (!selectedTaskIds.containsAll(reasonCodesByTask.keySet())) {
            throw new IllegalArgumentException("reason codes may only target deferred or blocked tasks");
        }
        if (!reasonCodesByTask.keySet().containsAll(selectedTaskIds)) {
            throw new IllegalArgumentException("every deferred or blocked task requires a controlled reason code");
        }
    }

    private static void addPartition(Set<String> allTaskIds, Set<String> partition, String message) {
        for (String taskId : partition) {
            if (!allTaskIds.add(taskId)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static Set<String> copyTaskIds(Set<String> taskIds, String name) {
        Objects.requireNonNull(taskIds, name);
        Set<String> copied = new LinkedHashSet<>();
        for (String taskId : taskIds) {
            copied.add(requireText(taskId, name));
        }
        return Set.copyOf(copied);
    }

    private static Map<String, String> copyReasons(Map<String, String> reasonCodesByTask) {
        Objects.requireNonNull(reasonCodesByTask, "reasonCodesByTask");
        Map<String, String> copied = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : reasonCodesByTask.entrySet()) {
            copied.put(requireText(entry.getKey(), "reasonTaskId"),
                    requireReasonCode(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    private static String requireReasonCode(String value) {
        String normalized = requireText(value, "reasonCode");
        if (!normalized.matches("[A-Z]+_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("reasonCode must be a public uppercase code");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
