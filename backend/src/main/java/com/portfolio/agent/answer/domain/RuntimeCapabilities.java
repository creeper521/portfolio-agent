package com.portfolio.agent.answer.domain;

public final class RuntimeCapabilities {

    private final boolean presetAnswers;
    private final boolean modelExpression;
    private final boolean groundedQuestions;
    private final boolean readOnlyTools;
    private final boolean multiTurnReferences;

    public RuntimeCapabilities(
            boolean presetAnswers,
            boolean modelExpression,
            boolean groundedQuestions,
            boolean readOnlyTools,
            boolean multiTurnReferences
    ) {
        this.presetAnswers = presetAnswers;
        this.modelExpression = modelExpression;
        this.groundedQuestions = groundedQuestions;
        this.readOnlyTools = readOnlyTools;
        this.multiTurnReferences = multiTurnReferences;
    }

    public boolean isPresetAnswers() { return presetAnswers; }
    public boolean isModelExpression() { return modelExpression; }
    public boolean isGroundedQuestions() { return groundedQuestions; }
    public boolean isReadOnlyTools() { return readOnlyTools; }
    public boolean isMultiTurnReferences() { return multiTurnReferences; }
}
