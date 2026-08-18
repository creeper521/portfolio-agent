package com.portfolio.agent.turn.planning;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SemanticPlanValidator {

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
