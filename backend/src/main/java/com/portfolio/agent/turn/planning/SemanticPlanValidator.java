package com.portfolio.agent.turn.planning;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义计划校验器：计划进入 Execution 阶段前的不变量闸门。
 *
 * <p>校验目标/任务数量与 ID 唯一性、履行任务存在性、依赖边合法性与
 * 无环性（Kahn 拓扑排序），以及跨域综合任务的扇入形状。全部通过才
 * 包装为 {@link ValidatedSemanticTurnPlan}。</p>
 */
public final class SemanticPlanValidator {

    /**
     * 校验计划并包装为已验证类型。
     *
     * @throws IllegalArgumentException 任一不变量不满足：目标/任务数量越界、
     *         重复 ID、履行任务缺失、依赖悬空或重复、依赖成环、
     *         跨域综合任务扇入形状错误
     */
    public ValidatedSemanticTurnPlan validate(SemanticTurnPlan plan) {
        if (plan.getUserGoals().isEmpty() || plan.getUserGoals().size() > 6) {
            throw new IllegalArgumentException("plan must contain one to six goals");
        }
        if (plan.getTasks().isEmpty() || plan.getTasks().size() > 18) {
            throw new IllegalArgumentException("plan task count is invalid");
        }
        Set<String> taskIds = new HashSet<>();
        for (SemanticTask task : plan.getTasks()) {
            if (!taskIds.add(task.getTaskId())) {
                throw new IllegalArgumentException("plan contains duplicate task ids");
            }
        }
        Set<String> goalIds = new HashSet<>();
        for (UserGoal goal : plan.getUserGoals()) {
            if (!goalIds.add(goal.getGoalId())) {
                throw new IllegalArgumentException("plan contains duplicate goal ids");
            }
            if (!taskIds.contains(goal.getFulfillmentTaskId())) {
                throw new IllegalArgumentException("goal fulfillment task does not exist");
            }
        }
        Set<String> edgeIds = new HashSet<>();
        Map<String, Integer> inbound = new HashMap<>();
        Map<String, List<String>> outbound = new HashMap<>();
        for (String taskId : taskIds) {
            inbound.put(taskId, 0);
            outbound.put(taskId, new java.util.ArrayList<>());
        }
        for (TaskDependency edge : plan.getDependencies()) {
            if (!taskIds.contains(edge.getFromTaskId()) || !taskIds.contains(edge.getToTaskId())) {
                throw new IllegalArgumentException("dependency references a missing task");
            }
            String edgeId = edge.getFromTaskId() + '>' + edge.getToTaskId();
            if (!edgeIds.add(edgeId)) {
                throw new IllegalArgumentException("plan contains duplicate dependencies");
            }
            inbound.put(edge.getToTaskId(), inbound.get(edge.getToTaskId()) + 1);
            outbound.get(edge.getFromTaskId()).add(edge.getToTaskId());
        }
        assertAcyclic(taskIds, inbound, outbound);
        assertCrossDomainFanIn(plan);
        return new ValidatedSemanticTurnPlan(plan);
    }

    /** Kahn 拓扑排序检查无环：访问任务数不足总数即存在环。 */
    private void assertAcyclic(
            Set<String> taskIds,
            Map<String, Integer> inbound,
            Map<String, List<String>> outbound) {
        ArrayDeque<String> ready = new ArrayDeque<>();
        taskIds.stream().filter(id -> inbound.get(id) == 0).sorted().forEach(ready::add);
        int visited = 0;
        while (!ready.isEmpty()) {
            String current = ready.removeFirst();
            visited++;
            for (String next : outbound.get(current)) {
                int remaining = inbound.get(next) - 1;
                inbound.put(next, remaining);
                if (remaining == 0) ready.addLast(next);
            }
        }
        if (visited != taskIds.size()) {
            throw new IllegalArgumentException("plan dependencies must be acyclic");
        }
    }

    /** 跨域综合任务必须恰好依赖一个 GENERAL_EXPLANATION 与一个 PORTFOLIO_FACT 输入。 */
    private void assertCrossDomainFanIn(SemanticTurnPlan plan) {
        for (SemanticTask task : plan.getTasks()) {
            if (task.getType() != SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS) continue;
            List<SemanticTask> sources = plan.getDependencies().stream()
                    .filter(edge -> edge.getToTaskId().equals(task.getTaskId()))
                    .map(edge -> plan.findTask(edge.getFromTaskId()).orElseThrow())
                    .toList();
            long general = sources.stream()
                    .filter(source -> source.getType() == SemanticTask.Type.GENERAL_EXPLANATION)
                    .count();
            long portfolio = sources.stream()
                    .filter(source -> source.getType() == SemanticTask.Type.PORTFOLIO_FACT)
                    .count();
            if (sources.size() != 2 || general != 1 || portfolio != 1) {
                throw new IllegalArgumentException(
                        "cross-domain synthesis requires one General and one Portfolio input");
            }
        }
    }
}
