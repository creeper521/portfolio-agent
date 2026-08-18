package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.turn.execution.TurnDeadline;

import java.util.Objects;

public record StructuredModelRequest(
        String operation, String systemPrompt, String userPrompt,
        int maxOutputTokens, double temperature, TurnDeadline deadline) {
    public StructuredModelRequest {
        operation = text(operation, "operation");
        systemPrompt = text(systemPrompt, "systemPrompt");
        userPrompt = text(userPrompt, "userPrompt");
        if (maxOutputTokens < 1 || maxOutputTokens > 8000
                || !Double.isFinite(temperature) || temperature < 0 || temperature > 1) {
            throw new IllegalArgumentException("model bounds are invalid");
        }
        Objects.requireNonNull(deadline, "deadline");
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
