package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.GeneratedAnswer;

public final class AnswerValidationResult {

    private final GeneratedAnswer answer;
    private final AnswerValidationFailureCode failureCode;

    private AnswerValidationResult(
            GeneratedAnswer answer,
            AnswerValidationFailureCode failureCode
    ) {
        this.answer = answer;
        this.failureCode = failureCode;
    }

    public static AnswerValidationResult accepted(GeneratedAnswer answer) {
        return new AnswerValidationResult(answer, null);
    }

    public static AnswerValidationResult rejected(AnswerValidationFailureCode failureCode) {
        return new AnswerValidationResult(null, failureCode);
    }

    public boolean isAccepted() { return answer != null; }
    public GeneratedAnswer getAnswer() { return answer; }
    public AnswerValidationFailureCode getFailureCode() { return failureCode; }
}
