package com.portfolio.agent.infrastructure.model.policy;

public final class ConversationProviderAccess {

    private final boolean allowed;

    public ConversationProviderAccess(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
