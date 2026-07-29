package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerRequestTimeoutException extends ApplicationException {

    public AnswerRequestTimeoutException() {
        super(
                AnswerErrorCode.ANSWER_REQUEST_TIMEOUT,
                AnswerErrorCode.ANSWER_REQUEST_TIMEOUT.getDefaultMessage()
        );
    }
}
