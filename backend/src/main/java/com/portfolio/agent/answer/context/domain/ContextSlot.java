package com.portfolio.agent.answer.context.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

/** Closed active-context slots; no arbitrary tool or context slots are allowed. */
public enum ContextSlot {
    ACTIVE_FACT_CONTEXT,
    ACTIVE_COMPARE_CONTEXT,
    ACTIVE_RECOMMENDATION;

    public static ContextSlot forTaskType(SemanticRoutingTypes.SemanticTaskType taskType) {
        return switch (taskType) {
            case PORTFOLIO_FACT -> ACTIVE_FACT_CONTEXT;
            case PORTFOLIO_COMPARE -> ACTIVE_COMPARE_CONTEXT;
            case PORTFOLIO_RECOMMEND, PORTFOLIO_REFINE_RECOMMENDATION -> ACTIVE_RECOMMENDATION;
            default -> throw new IllegalArgumentException("task type has no portfolio Context slot");
        };
    }

    public ConversationContextType contextType() {
        return this == ACTIVE_RECOMMENDATION
                ? ConversationContextType.RECOMMENDATION
                : ConversationContextType.RECENT_SEMANTIC_TASK;
    }
}
