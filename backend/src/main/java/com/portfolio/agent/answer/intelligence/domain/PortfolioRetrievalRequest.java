package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioRetrievalRequest {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private final String query;
    private final PortfolioTaskMode mode;
    private final PortfolioConditions conditions;
    private final int limit;

    public PortfolioRetrievalRequest(String query, PortfolioTaskMode mode, PortfolioConditions conditions) {
        this(query, mode, conditions, DEFAULT_LIMIT);
    }

    public PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit) {
        if (query == null || query.isBlank()) { throw new IllegalArgumentException("query is required"); }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.query = query.trim();
        this.mode = Objects.requireNonNull(mode, "mode");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.limit = limit;
    }

    public String getQuery() { return query; }
    public PortfolioTaskMode getMode() { return mode; }
    public PortfolioConditions getConditions() { return conditions; }
    public int getLimit() { return limit; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalRequest that)) { return false; }
        return Objects.equals(query, that.query) && mode == that.mode
                && Objects.equals(conditions, that.conditions) && limit == that.limit;
    }

    @Override
    public int hashCode() { return Objects.hash(query, mode, conditions, limit); }

    @Override
    public String toString() {
        return "PortfolioRetrievalRequest{" + "query='<redacted>'"
                + ", mode=" + mode + ", conditions=" + conditions + ", limit=" + limit + '}';
    }
}
