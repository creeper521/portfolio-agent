package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerAdmissionRejectedException extends ApplicationException {

    public AnswerAdmissionRejectedException(AnswerErrorCode errorCode, int retryAfterSeconds) {
        super(errorCode, errorCode.getDefaultMessage(), retryAfterSeconds);
    }
}
