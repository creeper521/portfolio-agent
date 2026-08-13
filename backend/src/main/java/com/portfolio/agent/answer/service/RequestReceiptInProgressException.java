package com.portfolio.agent.answer.service;

import java.time.Duration;

/** Safe application failure for a request token whose producer lease is still active. */
public final class RequestReceiptInProgressException extends RuntimeException {
    private final Duration retryAfter;

    public RequestReceiptInProgressException(Duration retryAfter) {
        super("REQUEST_IN_PROGRESS");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() { return retryAfter; }
}
