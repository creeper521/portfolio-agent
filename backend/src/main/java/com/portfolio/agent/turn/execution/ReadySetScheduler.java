package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.TaskDependency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 就绪集调度器：按依赖边推进计划的拓扑执行顺序。
 *
 * <p>只把"本身尚无结果且全部入边前置任务均已有结果"的任务判为就绪；
 * 依赖数据传递仅使用 {@link TaskOutcome.Produced} 的语义结果，任何其他
 * 终态（失败、拒绝、取消等）都不产生可传递数据。
 */
final class ReadySetScheduler {
    /**
     * 计算当前可调度的任务集合：任务本身尚无结果，且全部入边的前置任务
     * 均已出现结果（不要求前置是 Produced）。
     */
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

    /** 汇集指定任务全部入边上实际可用的语义结果；只有前置产出 Produced 时才有值。 */
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

    /**
     * 判断任务是否应标记 Blocked：存在入边但没有任何可传递的依赖语义结果
     * （即前置全部为非 Produced 终态），继续执行已无意义。
     */
    boolean blockedByDependencies(
            SemanticTurnPlan plan, String taskId, Map<String, TaskOutcome> outcomes) {
        List<TaskDependency> inbound = inbound(plan, taskId);
        return !inbound.isEmpty() && dependencyResults(plan, taskId, outcomes).isEmpty();
    }

    /** 汇集指向指定任务的全部依赖边。 */
    private List<TaskDependency> inbound(SemanticTurnPlan plan, String taskId) {
        return plan.getDependencies().stream()
                .filter(edge -> edge.getToTaskId().equals(taskId)).toList();
    }
}
