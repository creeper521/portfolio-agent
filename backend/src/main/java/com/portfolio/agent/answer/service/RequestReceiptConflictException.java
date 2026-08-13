package com.portfolio.agent.answer.service;

/** Safe application failure for a request token reused with a different request snapshot. */
public final class RequestReceiptConflictException extends RuntimeException {
    public RequestReceiptConflictException() {
        super("IDEMPOTENCY_KEY_CONFLICT");
    }
}
