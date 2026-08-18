package com.portfolio.agent.infrastructure.model;

public record StructuredModelResponse(String json) {
    public StructuredModelResponse {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("json is required");
    }
}
