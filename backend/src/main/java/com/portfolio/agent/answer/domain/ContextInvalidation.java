package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.context.domain.ConversationContextType;

import java.util.Objects;

public final class ContextInvalidation {
    private static final java.util.Set<String> REASONS = java.util.Set.of(
            "CONTEXT_RESULT_STALE", "REFERENCED_PUBLIC_SOURCE_CHANGED",
            "REFERENCED_SUBJECT_UNAVAILABLE", "CONTEXT_REFERENCE_INVALID",
            "CONTEXT_REFERENCE_EXPIRED", "RESULT_CONTEXT_AMBIGUITY");
    private static final java.util.Set<String> RECOVERY_ACTIONS = java.util.Set.of(
            "RESTART_FROM_CURRENT_CONTENT", "RESELECT_RESULTS", "REASK_WITHOUT_CONTEXT");
    private final String reasonCode;
    private final String recoveryAction;
    private final ConversationContextType contextType;
    private final String currentContentVersion;

    public ContextInvalidation(String reasonCode, String recoveryAction,
                               ConversationContextType contextType, String currentContentVersion) {
        this.reasonCode = requireClosed(reasonCode, "reasonCode", REASONS);
        this.recoveryAction = requireClosed(recoveryAction, "recoveryAction", RECOVERY_ACTIONS);
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.currentContentVersion = requireText(currentContentVersion, "currentContentVersion");
    }

    public String getReasonCode() { return reasonCode; }
    public String getRecoveryAction() { return recoveryAction; }
    public ConversationContextType getContextType() { return contextType; }
    public String getCurrentContentVersion() { return currentContentVersion; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private static String requireClosed(String value, String name, java.util.Set<String> allowed) {
        String normalized = requireText(value, name);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(name + " is unsupported");
        return normalized;
    }
}
