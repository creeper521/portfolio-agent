package com.portfolio.agent.turn.execution;

import java.util.Objects;

/** Executor result before the Engine binds the immutable task identity. */
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
