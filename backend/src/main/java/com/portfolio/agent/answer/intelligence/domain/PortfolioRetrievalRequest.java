package com.portfolio.agent.answer.intelligence.domain;

import java.util.HashSet;
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
    private final boolean exactPortfolioLookup;

    public PortfolioRetrievalRequest(String query, PortfolioTaskMode mode, PortfolioConditions conditions) {
        this(query, mode, conditions, DEFAULT_LIMIT, List.of(), false);
    }

    public PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit) {
        this(query, mode, conditions, limit, List.of(), false);
    }

    private PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit,
            List<String> requiredPortfolioIds,
            boolean exactPortfolioLookup) {
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
        this.exactPortfolioLookup = exactPortfolioLookup;
    }

    public static PortfolioRetrievalRequest contextValidation(
            PortfolioConditions conditions,
            List<String> selectedPortfolioIds) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(selectedPortfolioIds, "selectedPortfolioIds");
        if (selectedPortfolioIds.size() > 5
                || selectedPortfolioIds.size() > conditions.getRequestedSize()) {
            throw new IllegalArgumentException("selectedPortfolioIds exceed the requested size");
        }
        if (new HashSet<>(selectedPortfolioIds).size() != selectedPortfolioIds.size()) {
            throw new IllegalArgumentException("selectedPortfolioIds must be unique");
        }
        if (selectedPortfolioIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("selectedPortfolioIds must not contain blank values");
        }
        return new PortfolioRetrievalRequest(
                "portfolio-context-validation",
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                conditions,
                Math.max(1, selectedPortfolioIds.size()),
                selectedPortfolioIds,
                true);
    }

    public static PortfolioRetrievalRequest subjectScope(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return new PortfolioRetrievalRequest(
                query,
                mode,
                conditions,
                DEFAULT_LIMIT,
                List.of(subjectId.trim()),
                true);
    }

    public String getQuery() { return query; }
    public PortfolioTaskMode getMode() { return mode; }
    public PortfolioConditions getConditions() { return conditions; }
    public int getLimit() { return limit; }
    public List<String> getRequiredPortfolioIds() { return requiredPortfolioIds; }
    public boolean isExactPortfolioLookup() { return exactPortfolioLookup; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalRequest that)) { return false; }
        return Objects.equals(query, that.query) && mode == that.mode
                && Objects.equals(conditions, that.conditions) && limit == that.limit
                && Objects.equals(requiredPortfolioIds, that.requiredPortfolioIds)
                && exactPortfolioLookup == that.exactPortfolioLookup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, mode, conditions, limit, requiredPortfolioIds, exactPortfolioLookup);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievalRequest{" + "query='<redacted>'"
                + ", mode=" + mode + ", conditions=" + conditions + ", limit=" + limit
                + ", requiredPortfolioIdCount=" + requiredPortfolioIds.size() + '}';
    }
}
