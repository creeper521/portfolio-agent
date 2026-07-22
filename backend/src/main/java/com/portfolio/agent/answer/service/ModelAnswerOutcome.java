package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;

import java.util.Objects;

public final class ModelAnswerOutcome {

    private final GeneratedAnswer answer;
    private final GenerationMode generationMode;
    private final ModelExpressionFailureCode failureCode;

    public ModelAnswerOutcome(
            GeneratedAnswer answer,
            GenerationMode generationMode,
            ModelExpressionFailureCode failureCode
    ) {
        this.answer = Objects.requireNonNull(answer, "answer");
        this.generationMode = Objects.requireNonNull(generationMode, "generationMode");
        this.failureCode = failureCode;
    }

    public GeneratedAnswer getAnswer() { return answer; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public ModelExpressionFailureCode getFailureCode() { return failureCode; }
}
