package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ContinuationReference {
    private final String contextHandle;
    private final String resultItemId;
    public ContinuationReference(String contextHandle, String resultItemId) {
        if (contextHandle == null || contextHandle.isBlank()) {
            throw new IllegalArgumentException("contextHandle is required");
        }
        this.contextHandle = contextHandle.trim();
        this.resultItemId = resultItemId == null ? null : text(resultItemId);
    }
    public String getContextHandle() { return contextHandle; }
    public String getResultItemId() { return resultItemId; }
    private static String text(String value) {
        if (value.isBlank()) throw new IllegalArgumentException("resultItemId is invalid");
        return value.trim();
    }
}
