package com.portfolio.agent.turn.execution;

import java.util.Objects;

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
