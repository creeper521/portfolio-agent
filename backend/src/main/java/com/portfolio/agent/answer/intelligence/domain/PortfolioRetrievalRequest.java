package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievalRequest {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private final String query;
    private final PortfolioTaskMode mode;
    private final PortfolioConditions conditions;
    private final int limit;
    private final List<String> requiredPortfolioIds;

    public PortfolioRetrievalRequest(String query, PortfolioTaskMode mode, PortfolioConditions conditions) {
        this(query, mode, conditions, DEFAULT_LIMIT, List.of());
    }

    public PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit) {
        this(query, mode, conditions, limit, List.of());
    }

    private PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit,
            List<String> requiredPortfolioIds) {
        if (query == null || query.isBlank()) { throw new IllegalArgumentException("query is required"); }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.query = query.trim();
        this.mode = Objects.requireNonNull(mode, "mode");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.limit = limit;
        this.requiredPortfolioIds = List.copyOf(
                Objects.requireNonNull(requiredPortfolioIds, "requiredPortfolioIds"));
    }

    public static PortfolioRetrievalRequest contextValidation(
            PortfolioConditions conditions,
            List<String> selectedPortfolioIds) {
        if (selectedPortfolioIds == null || selectedPortfolioIds.isEmpty()) {
            throw new IllegalArgumentException("selectedPortfolioIds are required");
        }
        return new PortfolioRetrievalRequest(
                "portfolio-context-validation",
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                conditions,
                Math.min(MAX_LIMIT, selectedPortfolioIds.size()),
                selectedPortfolioIds);
    }

    public String getQuery() { return query; }
    public PortfolioTaskMode getMode() { return mode; }
    public PortfolioConditions getConditions() { return conditions; }
    public int getLimit() { return limit; }
    public List<String> getRequiredPortfolioIds() { return requiredPortfolioIds; }
    public boolean isExactPortfolioLookup() { return !requiredPortfolioIds.isEmpty(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalRequest that)) { return false; }
        return Objects.equals(query, that.query) && mode == that.mode
                && Objects.equals(conditions, that.conditions) && limit == that.limit
                && Objects.equals(requiredPortfolioIds, that.requiredPortfolioIds);
    }

    @Override
    public int hashCode() { return Objects.hash(query, mode, conditions, limit, requiredPortfolioIds); }

    @Override
    public String toString() {
        return "PortfolioRetrievalRequest{" + "query='<redacted>'"
                + ", mode=" + mode + ", conditions=" + conditions + ", limit=" + limit
                + ", requiredPortfolioIdCount=" + requiredPortfolioIds.size() + '}';
    }
}
