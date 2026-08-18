package com.portfolio.agent.infrastructure.model;

public interface StructuredModelTransport {
    StructuredModelResponse execute(StructuredModelRequest request) throws StructuredModelFailure;
}
