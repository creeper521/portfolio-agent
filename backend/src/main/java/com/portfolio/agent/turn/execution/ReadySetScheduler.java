package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.TaskDependency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReadySetScheduler {
    List<SemanticTask> ready(SemanticTurnPlan plan, Map<String, TaskOutcome> outcomes) {
        List<SemanticTask> ready = new ArrayList<>();
        for (SemanticTask task : plan.getTasks()) {
            if (outcomes.containsKey(task.getTaskId())) continue;
            List<TaskDependency> inbound = inbound(plan, task.getTaskId());
            if (inbound.stream().allMatch(edge -> outcomes.containsKey(edge.getFromTaskId()))) {
                ready.add(task);
            }
        }
        return List.copyOf(ready);
    }

    List<TaskSemanticResult> dependencyResults(
            SemanticTurnPlan plan, String taskId, Map<String, TaskOutcome> outcomes) {
        List<TaskSemanticResult> results = new ArrayList<>();
        for (TaskDependency edge : inbound(plan, taskId)) {
            TaskOutcome outcome = outcomes.get(edge.getFromTaskId());
            if (outcome != null && outcome.getProducedArtifact().isPresent()) {
                results.add(outcome.getProducedArtifact().orElseThrow().getSemanticResult());
            }
        }
        return List.copyOf(results);
    }

    boolean blockedByDependencies(
            SemanticTurnPlan plan, String taskId, Map<String, TaskOutcome> outcomes) {
        List<TaskDependency> inbound = inbound(plan, taskId);
        return !inbound.isEmpty() && dependencyResults(plan, taskId, outcomes).isEmpty();
    }

    private List<TaskDependency> inbound(SemanticTurnPlan plan, String taskId) {
        return plan.getDependencies().stream()
                .filter(edge -> edge.getToTaskId().equals(taskId)).toList();
    }
}
