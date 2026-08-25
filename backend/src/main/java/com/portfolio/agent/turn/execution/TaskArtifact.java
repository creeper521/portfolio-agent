package com.portfolio.agent.turn.execution;

import java.util.Objects;

/**
 * 任务成功产出物的不可变三元组：语义结果（可跨数据边传递）、展示片段
 * （仅本任务所有、不作为下游输入）、公开溯源键列表。三者均必填。
 */
public final class TaskArtifact {
    private final TaskSemanticResult semanticResult;
    private final TaskPresentation presentation;
    private final TaskProvenance provenance;

    public TaskArtifact(
            TaskSemanticResult semanticResult,
            TaskPresentation presentation,
            TaskProvenance provenance) {
        this.semanticResult = Objects.requireNonNull(semanticResult, "semanticResult");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public TaskSemanticResult getSemanticResult() { return semanticResult; }
    public TaskPresentation getPresentation() { return presentation; }
    public TaskProvenance getProvenance() { return provenance; }
}
