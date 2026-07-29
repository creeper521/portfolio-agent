package com.portfolio.agent.common.web;

import java.util.Objects;

public final class RequestFailure {

    private final String errorCode;
    private final String exceptionType;
    private final String safeRenderedFrames;

    public RequestFailure(String errorCode, String exceptionType, String safeRenderedFrames) {
        this.errorCode = Objects.requireNonNull(errorCode, "error code must not be null");
        this.exceptionType = Objects.requireNonNull(exceptionType, "exception type must not be null");
        this.safeRenderedFrames = Objects.requireNonNull(
                safeRenderedFrames, "safe rendered frames must not be null");
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getSafeRenderedFrames() {
        return safeRenderedFrames;
    }
}
