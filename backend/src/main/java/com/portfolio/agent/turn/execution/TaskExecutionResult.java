package com.portfolio.agent.turn.execution;

import java.util.Objects;

/**
 * Executor result before the Engine binds the immutable task identity.
 *
 * <p>Executor 的成功返回值，此时尚未绑定任务身份（taskId 由 Engine 统一附加）：
 * 一个 {@link TaskArtifact} 加满足度等级。{@code full}/{@code partial} 工厂
 * 分别表达"完全满足目标"与"部分满足目标"两种产出。
 */
public final class TaskExecutionResult {
    private final TaskArtifact artifact;
    private final TaskOutcome.Fulfillment fulfillment;

    public TaskExecutionResult(TaskArtifact artifact, TaskOutcome.Fulfillment fulfillment) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.fulfillment = Objects.requireNonNull(fulfillment, "fulfillment");
    }

    public static TaskExecutionResult full(TaskArtifact artifact) {
        return new TaskExecutionResult(artifact, TaskOutcome.Fulfillment.FULL);
    }

    public static TaskExecutionResult partial(TaskArtifact artifact) {
        return new TaskExecutionResult(artifact, TaskOutcome.Fulfillment.PARTIAL);
    }

    public TaskArtifact getArtifact() { return artifact; }
    public TaskOutcome.Fulfillment getFulfillment() { return fulfillment; }
}
