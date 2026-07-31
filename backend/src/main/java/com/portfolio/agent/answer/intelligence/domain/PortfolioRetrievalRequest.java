package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioRetrievalRequest {

    private final String query;
    private final PortfolioTaskMode mode;
    private final PortfolioConditions conditions;

    public PortfolioRetrievalRequest(String query, PortfolioTaskMode mode, PortfolioConditions conditions) {
        if (query == null || query.isBlank()) { throw new IllegalArgumentException("query is required"); }
        this.query = query.trim();
        this.mode = Objects.requireNonNull(mode, "mode");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    public String getQuery() { return query; }
    public PortfolioTaskMode getMode() { return mode; }
    public PortfolioConditions getConditions() { return conditions; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalRequest that)) { return false; }
        return Objects.equals(query, that.query) && mode == that.mode
                && Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() { return Objects.hash(query, mode, conditions); }

    @Override
    public String toString() {
        return "PortfolioRetrievalRequest{" + "query='<redacted>'"
                + ", mode=" + mode + ", conditions=" + conditions + '}';
    }
}
