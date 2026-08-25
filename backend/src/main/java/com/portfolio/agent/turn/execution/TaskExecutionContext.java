package com.portfolio.agent.turn.execution;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.planning.SemanticTask;

import java.util.List;
import java.util.Objects;

/**
 * 单个任务的执行上下文，不可变：Engine 为一次任务执行打包的全部输入——
 * 任务本体、上游语义结果、内容发布 ID、共享的 TurnDeadline 与 CancellationSignal、
 * 模型表达授权、预设请求标记，以及 Claim 后冻结的无凭证模型执行快照。
 * Capability 只经由该上下文接触 Turn 级资源，无法自行延长预算或绕过取消。
 */
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
