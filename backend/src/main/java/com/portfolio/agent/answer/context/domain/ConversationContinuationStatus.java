package com.portfolio.agent.answer.context.domain;

public enum ConversationContinuationStatus {
    AVAILABLE,
    CONTEXT_EXPIRED,
    CONTEXT_CLEARED,
    CONTEXT_UNAVAILABLE,
    PERSISTENCE_UNAVAILABLE,
    NOT_APPLICABLE
}
