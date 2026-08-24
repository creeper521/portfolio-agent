package com.portfolio.agent.infrastructure.model;

public interface StructuredModelTransport {
    /** Executes one request against the configured provider; callers do not repair or fall back. */
    StructuredModelResponse execute(StructuredModelRequest request) throws StructuredModelFailure;
}
