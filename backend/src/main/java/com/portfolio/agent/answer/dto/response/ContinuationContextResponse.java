package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

import java.util.Objects;

/** Authorized continuation metadata for one completed task. */
public final class ContinuationContextResponse {
    private final String contextHandle;
    private final ConversationContextType contextType;
    private final String sourceTaskId;

    public ContinuationContextResponse(
            String contextHandle, ConversationContextType contextType, String sourceTaskId) {
        this.contextHandle = requireText(contextHandle, "contextHandle");
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.sourceTaskId = requireText(sourceTaskId, "sourceTaskId");
    }

    public String getContextHandle() { return contextHandle; }
    public ConversationContextType getContextType() { return contextType; }
    public String getSourceTaskId() { return sourceTaskId; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
