package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class ModelExpressionRequest {

    private final String answerSchemaVersion;
    private final AnswerPlan answerPlan;

    public ModelExpressionRequest(String answerSchemaVersion, AnswerPlan answerPlan) {
        if (answerSchemaVersion == null || answerSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("answerSchemaVersion is required");
        }
        this.answerSchemaVersion = answerSchemaVersion;
        this.answerPlan = Objects.requireNonNull(answerPlan, "answerPlan");
    }

    public String getAnswerSchemaVersion() { return answerSchemaVersion; }
    public AnswerPlan getAnswerPlan() { return answerPlan; }
}
