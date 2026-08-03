package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.ConversationIntent;

import java.util.Objects;

public final class PortfolioTaskRoutingDecision {

    private final ConversationIntent boundaryIntent;
    private final PortfolioTask task;
    private final boolean notPortfolio;

    private PortfolioTaskRoutingDecision(
            ConversationIntent boundaryIntent,
            PortfolioTask task,
            boolean notPortfolio) {
        this.boundaryIntent = boundaryIntent;
        this.task = task;
        this.notPortfolio = notPortfolio;
    }

    public static PortfolioTaskRoutingDecision boundary(ConversationIntent boundaryIntent) {
        Objects.requireNonNull(boundaryIntent, "boundaryIntent");
        if (boundaryIntent != ConversationIntent.TIME_SENSITIVE
                && boundaryIntent != ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            throw new IllegalArgumentException("boundaryIntent is not allowed");
        }
        return new PortfolioTaskRoutingDecision(boundaryIntent, null, false);
    }

    public static PortfolioTaskRoutingDecision task(PortfolioTask task) {
        return new PortfolioTaskRoutingDecision(
                null, Objects.requireNonNull(task, "task"), false);
    }

    public static PortfolioTaskRoutingDecision notPortfolio() {
        return new PortfolioTaskRoutingDecision(null, null, true);
    }

    public ConversationIntent getBoundaryIntent() {
        return boundaryIntent;
    }

    public PortfolioTask getTask() {
        return task;
    }

    public boolean isNotPortfolio() { return notPortfolio; }
}
