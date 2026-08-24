package com.portfolio.agent.turn.execution;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.planning.SemanticTask;

import java.util.List;
import java.util.Objects;

public final class TaskExecutionContext {
    private final SemanticTask task;
    private final List<TaskSemanticResult> dependencyResults;
    private final String contentReleaseId;
    private final TurnDeadline deadline;
    private final CancellationSignal cancellation;
    private final boolean modelExpressionAllowed;
    private final boolean presetRequest;
    private final ResolvedModelExecution modelExecution;

    public TaskExecutionContext(
            SemanticTask task, List<TaskSemanticResult> dependencyResults,
            String contentReleaseId, TurnDeadline deadline, CancellationSignal cancellation,
            boolean modelExpressionAllowed, boolean presetRequest,
            ResolvedModelExecution modelExecution) {
        this.task = Objects.requireNonNull(task, "task");
        this.dependencyResults = List.copyOf(Objects.requireNonNull(dependencyResults, "dependencyResults"));
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.modelExpressionAllowed = modelExpressionAllowed;
        this.presetRequest = presetRequest;
        this.modelExecution = Objects.requireNonNull(
                modelExecution, "modelExecution");
    }

    public SemanticTask getTask() { return task; }
    public List<TaskSemanticResult> getDependencyResults() { return dependencyResults; }
    public String getContentReleaseId() { return contentReleaseId; }
    public TurnDeadline getDeadline() { return deadline; }
    public CancellationSignal getCancellation() { return cancellation; }
    public boolean isModelExpressionAllowed() { return modelExpressionAllowed; }
    public boolean isPresetRequest() { return presetRequest; }
    public ResolvedModelExecution getModelExecution() { return modelExecution; }
}
