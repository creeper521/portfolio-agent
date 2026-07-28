package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerAdmissionRejectedException extends ApplicationException {

    private final int retryAfterSeconds;

    public AnswerAdmissionRejectedException(AnswerErrorCode errorCode, int retryAfterSeconds) {
        super(errorCode, errorCode.getDefaultMessage());
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
