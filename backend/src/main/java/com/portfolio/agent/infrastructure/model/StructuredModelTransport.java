package com.portfolio.agent.infrastructure.model;

public interface StructuredModelTransport {
    /** Executes one request against the resolved binding; callers do not repair or fall back. */
    StructuredModelResponse execute(
            ModelTransportBinding binding,
            StructuredModelRequest request) throws StructuredModelFailure;
}
