package com.portfolio.agent.common.exception;

import java.util.Objects;

public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Integer retryAfterSeconds;

    public ApplicationException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApplicationException(
            ErrorCode errorCode,
            String message,
            Integer retryAfterSeconds
    ) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (retryAfterSeconds != null && retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
