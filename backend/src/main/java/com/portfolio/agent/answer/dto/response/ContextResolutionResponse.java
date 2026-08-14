package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

public final class ContextResolutionResponse {
    private final String mode;
    private final ConversationContextType contextType;
    private final String currentContentVersion;

    public ContextResolutionResponse(String mode, ConversationContextType contextType, String currentContentVersion) {
        this.mode = mode;
        this.contextType = contextType;
        this.currentContentVersion = currentContentVersion;
    }

    public String getMode() { return mode; }
    public ConversationContextType getContextType() { return contextType; }
    public String getCurrentContentVersion() { return currentContentVersion; }
}
