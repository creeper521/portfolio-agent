package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class InvalidAnswerContextException extends ApplicationException {

    public InvalidAnswerContextException() {
        super(
                AnswerErrorCode.INVALID_ANSWER_CONTEXT,
                AnswerErrorCode.INVALID_ANSWER_CONTEXT.getDefaultMessage()
        );
    }
}
