package com.portfolio.agent.answer.domain;

public final class ModelExpressionResult {

    private final ModelAnswerDraft draft;
    private final ModelExpressionFailureCode failureCode;

    private ModelExpressionResult(
            ModelAnswerDraft draft,
            ModelExpressionFailureCode failureCode
    ) {
        this.draft = draft;
        this.failureCode = failureCode;
    }

    public static ModelExpressionResult success(ModelAnswerDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft is required");
        }
        return new ModelExpressionResult(draft, null);
    }

    public static ModelExpressionResult failure(ModelExpressionFailureCode failureCode) {
        if (failureCode == null) {
            throw new IllegalArgumentException("failureCode is required");
        }
        return new ModelExpressionResult(null, failureCode);
    }

    public boolean isSuccessful() { return draft != null; }
    public ModelAnswerDraft getDraft() { return draft; }
    public ModelExpressionFailureCode getFailureCode() { return failureCode; }
}
