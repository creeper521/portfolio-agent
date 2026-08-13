package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

import java.util.Objects;

/** Typed compatibility criteria used by Context resolution. */
public final class ConversationContextLookupCriteria {
    private final ConversationContextType contextType;
    private final SemanticRoutingTypes.SemanticTaskType taskType;

    public ConversationContextLookupCriteria(
            ConversationContextType contextType,
            SemanticRoutingTypes.SemanticTaskType taskType) {
        this.contextType = Objects.requireNonNull(contextType, "contextType");
        this.taskType = taskType;
        if (taskType != null && contextType != ConversationContextType.RECENT_SEMANTIC_TASK) {
            throw new IllegalArgumentException("task type compatibility only applies to recent task Context");
        }
    }

    public ConversationContextType getContextType() { return contextType; }
    public SemanticRoutingTypes.SemanticTaskType getTaskType() { return taskType; }
}
