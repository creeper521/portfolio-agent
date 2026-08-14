package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

import java.util.Objects;

public final class ContextResolution {
    private final String mode;
    private final ConversationContextType contextType;
    private final String currentContentVersion;

    public ContextResolution(String mode, ConversationContextType contextType, String currentContentVersion) {
        this.mode = requireText(mode, "mode");
        if (!"REVALIDATED_TO_CURRENT".equals(this.mode)) {
            throw new IllegalArgumentException("mode is unsupported");
        }
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.currentContentVersion = requireText(currentContentVersion, "currentContentVersion");
    }

    public String getMode() { return mode; }
    public ConversationContextType getContextType() { return contextType; }
    public String getCurrentContentVersion() { return currentContentVersion; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
