package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class AgentExecutionSnapshot {

    private final AnswerTurnSnapshot turnSnapshot;
    private final String audienceProfileVersion;
    private final String modelPolicyVersion;
    private final String answerSchemaVersion;
    private final boolean modelExpressionEnabled;
    private final String retrievalPolicyVersion;
    private final boolean groundedQuestionsEnabled;
    private final RetrievalMode retrievalMode;
    private final String toolPolicyVersion;
    private final boolean readOnlyToolsEnabled;
    private final boolean multiTurnReferencesEnabled;
    private final String registrySnapshotVersion;
    private final ExecutionBudgets budgets;

    public AgentExecutionSnapshot(
            AnswerTurnSnapshot turnSnapshot,
            String audienceProfileVersion,
            String modelPolicyVersion,
            String answerSchemaVersion,
            boolean modelExpressionEnabled,
            ExecutionBudgets budgets
    ) {
        this(turnSnapshot, audienceProfileVersion, modelPolicyVersion, answerSchemaVersion,
                modelExpressionEnabled, "none", false, RetrievalMode.KEYWORD_ONLY, budgets);
    }

    public AgentExecutionSnapshot(
            AnswerTurnSnapshot turnSnapshot,
            String audienceProfileVersion,
            String modelPolicyVersion,
            String answerSchemaVersion,
            boolean modelExpressionEnabled,
            String retrievalPolicyVersion,
            boolean groundedQuestionsEnabled,
            RetrievalMode retrievalMode,
            ExecutionBudgets budgets
    ) {
        this(turnSnapshot, audienceProfileVersion, modelPolicyVersion, answerSchemaVersion,
                modelExpressionEnabled, retrievalPolicyVersion, groundedQuestionsEnabled,
                retrievalMode, "none", false, false, "none", budgets);
    }

    public AgentExecutionSnapshot(
            AnswerTurnSnapshot turnSnapshot,
            String audienceProfileVersion,
            String modelPolicyVersion,
            String answerSchemaVersion,
            boolean modelExpressionEnabled,
            String retrievalPolicyVersion,
            boolean groundedQuestionsEnabled,
            RetrievalMode retrievalMode,
            String toolPolicyVersion,
            boolean readOnlyToolsEnabled,
            boolean multiTurnReferencesEnabled,
            String registrySnapshotVersion,
            ExecutionBudgets budgets
    ) {
        this.turnSnapshot = Objects.requireNonNull(turnSnapshot, "turnSnapshot");
        this.audienceProfileVersion = audienceProfileVersion;
        this.modelPolicyVersion = modelPolicyVersion;
        this.answerSchemaVersion = answerSchemaVersion;
        this.modelExpressionEnabled = modelExpressionEnabled;
        this.retrievalPolicyVersion = retrievalPolicyVersion;
        this.groundedQuestionsEnabled = groundedQuestionsEnabled;
        this.retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        this.toolPolicyVersion = toolPolicyVersion;
        this.readOnlyToolsEnabled = readOnlyToolsEnabled;
        this.multiTurnReferencesEnabled = multiTurnReferencesEnabled;
        if (registrySnapshotVersion == null || registrySnapshotVersion.isBlank()) {
            throw new IllegalArgumentException("registrySnapshotVersion must not be blank");
        }
        this.registrySnapshotVersion = registrySnapshotVersion;
        this.budgets = Objects.requireNonNull(budgets, "budgets");
    }

    public AnswerTurnSnapshot getTurnSnapshot() { return turnSnapshot; }
    public String getAudienceProfileVersion() { return audienceProfileVersion; }
    public String getModelPolicyVersion() { return modelPolicyVersion; }
    public String getAnswerSchemaVersion() { return answerSchemaVersion; }
    public boolean isModelExpressionEnabled() { return modelExpressionEnabled; }
    public String getRetrievalPolicyVersion() { return retrievalPolicyVersion; }
    public boolean isGroundedQuestionsEnabled() { return groundedQuestionsEnabled; }
    public RetrievalMode getRetrievalMode() { return retrievalMode; }
    public ExecutionBudgets getBudgets() { return budgets; }
    public String getToolPolicyVersion() { return toolPolicyVersion; }
    public boolean isReadOnlyToolsEnabled() { return readOnlyToolsEnabled; }
    public boolean isMultiTurnReferencesEnabled() { return multiTurnReferencesEnabled; }
    public String getRegistrySnapshotVersion() { return registrySnapshotVersion; }
}
