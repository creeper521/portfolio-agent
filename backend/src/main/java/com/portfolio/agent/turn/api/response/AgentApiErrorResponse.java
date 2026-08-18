package com.portfolio.agent.turn.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentApiErrorResponse(UUID requestId, Error error) {
    public record Error(
            String code, String message, boolean retryable,
            Long retryAfterSeconds) { }
    public static AgentApiErrorResponse of(
            UUID requestId, String code, String message,
            boolean retryable, Long retryAfterSeconds) {
        return new AgentApiErrorResponse(
                requestId, new Error(code, message, retryable, retryAfterSeconds));
    }
}
