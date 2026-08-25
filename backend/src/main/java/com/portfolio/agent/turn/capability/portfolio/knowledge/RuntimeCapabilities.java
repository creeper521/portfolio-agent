package com.portfolio.agent.turn.capability.portfolio.knowledge;

/**
 * 运行时能力开关集（不可变值对象）。
 *
 * <p>五个开关依次表示：预设问题答案、模型表达、可落地提问（grounded）、
 * 只读工具、多轮稳定引用；由 RuntimeAnswerContent 依据内容是否存在派生，
 * 用于向上层声明当前快照支持哪些回答能力。
 */
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
