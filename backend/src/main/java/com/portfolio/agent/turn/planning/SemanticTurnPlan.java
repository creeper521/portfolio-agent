package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 语义 Turn 计划：一轮 Turn 的目标、任务与依赖的不可变快照。
 *
 * <p>由 {@link SemanticPlanCompiler} 生成、{@link SemanticPlanValidator}
 * 校验后进入 Execution 阶段；contentReleaseId 锁定计划依据的内容发布。</p>
 */
public final class SemanticTurnPlan {
    private final String contentReleaseId;
    private final List<UserGoal> userGoals;
    private final List<SemanticTask> tasks;
    private final List<TaskDependency> dependencies;

    public SemanticTurnPlan(
            String contentReleaseId,
            List<UserGoal> userGoals,
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies) {
        if (contentReleaseId == null || contentReleaseId.isBlank()) {
            throw new IllegalArgumentException("contentReleaseId is required");
        }
        this.contentReleaseId = contentReleaseId;
        this.userGoals = List.copyOf(Objects.requireNonNull(userGoals, "userGoals"));
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    public String getContentReleaseId() { return contentReleaseId; }
    public List<UserGoal> getUserGoals() { return userGoals; }
    public List<SemanticTask> getTasks() { return tasks; }
    public List<TaskDependency> getDependencies() { return dependencies; }

    /** 按任务 ID 查找任务，找不到返回 Optional.empty()。 */
    public Optional<SemanticTask> findTask(String taskId) {
        return tasks.stream().filter(task -> task.getTaskId().equals(taskId)).findFirst();
    }
}
