package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.context.domain.ConversationContextType;

public final class ContextInvalidationResponse {
    private final String reasonCode;
    private final String recoveryAction;
    private final ConversationContextType contextType;
    private final String currentContentVersion;

    public ContextInvalidationResponse(String reasonCode, String recoveryAction,
                                       ConversationContextType contextType, String currentContentVersion) {
        this.reasonCode = reasonCode;
        this.recoveryAction = recoveryAction;
        this.contextType = contextType;
        this.currentContentVersion = currentContentVersion;
    }

    public String getReasonCode() { return reasonCode; }
    public String getRecoveryAction() { return recoveryAction; }
    public ConversationContextType getContextType() { return contextType; }
    public String getCurrentContentVersion() { return currentContentVersion; }
}
