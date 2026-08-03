package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.ConversationIntent;

import java.util.Objects;

public final class PortfolioTaskRoutingDecision {

    private final ConversationIntent boundaryIntent;
    private final PortfolioTask task;

    private PortfolioTaskRoutingDecision(
            ConversationIntent boundaryIntent,
            PortfolioTask task) {
        this.boundaryIntent = boundaryIntent;
        this.task = task;
    }

    public static PortfolioTaskRoutingDecision boundary(ConversationIntent boundaryIntent) {
        Objects.requireNonNull(boundaryIntent, "boundaryIntent");
        if (boundaryIntent != ConversationIntent.TIME_SENSITIVE
                && boundaryIntent != ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            throw new IllegalArgumentException("boundaryIntent is not allowed");
        }
        return new PortfolioTaskRoutingDecision(boundaryIntent, null);
    }

    public static PortfolioTaskRoutingDecision task(PortfolioTask task) {
        return new PortfolioTaskRoutingDecision(null, Objects.requireNonNull(task, "task"));
    }

    public ConversationIntent getBoundaryIntent() {
        return boundaryIntent;
    }

    public PortfolioTask getTask() {
        return task;
    }
}
