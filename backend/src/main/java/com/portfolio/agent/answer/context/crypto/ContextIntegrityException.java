package com.portfolio.agent.answer.context.crypto;

/** Fail-closed typed integrity error with no cryptographic detail in its message. */
public final class ContextIntegrityException extends RuntimeException {
    public ContextIntegrityException() { super("CONTEXT_INTEGRITY_FAILURE"); }
}
