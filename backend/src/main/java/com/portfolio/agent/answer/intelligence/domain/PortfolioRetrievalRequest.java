package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;

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
    private final List<String> requiredClaimIds;
    private final boolean exactPortfolioLookup;
    private final PortfolioRetrievalStrategy strategy;
    private final List<AnswerClaimCategory> preferredClaimCategories;

    public PortfolioRetrievalRequest(String query, PortfolioTaskMode mode, PortfolioConditions conditions) {
        this(query, mode, conditions, DEFAULT_LIMIT, List.of(), List.of(), false,
                PortfolioRetrievalStrategy.RELEVANCE, List.of());
    }

    public PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit) {
        this(query, mode, conditions, limit, List.of(), List.of(), false,
                PortfolioRetrievalStrategy.RELEVANCE, List.of());
    }

    private PortfolioRetrievalRequest(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            int limit,
            List<String> requiredPortfolioIds,
            List<String> requiredClaimIds,
            boolean exactPortfolioLookup,
            PortfolioRetrievalStrategy strategy,
            List<AnswerClaimCategory> preferredClaimCategories) {
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
        this.requiredClaimIds = List.copyOf(
                Objects.requireNonNull(requiredClaimIds, "requiredClaimIds"));
        this.exactPortfolioLookup = exactPortfolioLookup;
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.preferredClaimCategories = List.copyOf(Objects.requireNonNull(
                preferredClaimCategories, "preferredClaimCategories"));
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
                List.of(),
                true,
                PortfolioRetrievalStrategy.CONTEXT_VALIDATION,
                List.of());
    }

    public static PortfolioRetrievalRequest subjectScope(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            String subjectId) {
        return subjectScope(query, mode, conditions, subjectId, List.of());
    }

    public static PortfolioRetrievalRequest subjectScope(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            String subjectId,
            List<AnswerClaimCategory> preferredClaimCategories) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return new PortfolioRetrievalRequest(
                query,
                mode,
                conditions,
                DEFAULT_LIMIT,
                List.of(subjectId.trim()),
                List.of(),
                true,
                PortfolioRetrievalStrategy.SUBJECT_SCOPED_RELEVANCE,
                preferredClaimCategories);
    }

    public static PortfolioRetrievalRequest referenceScope(
            String query,
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            List<String> subjectIds,
            List<String> claimIds,
            List<AnswerClaimCategory> preferredClaimCategories) {
        validateUniqueNonBlank(subjectIds, "subjectIds");
        validateUniqueNonBlank(claimIds, "claimIds");
        if (subjectIds.isEmpty()) {
            throw new IllegalArgumentException("subjectIds are required");
        }
        return new PortfolioRetrievalRequest(
                query,
                mode,
                conditions,
                DEFAULT_LIMIT,
                subjectIds,
                claimIds,
                true,
                PortfolioRetrievalStrategy.REFERENCE_SCOPED,
                preferredClaimCategories);
    }

    private static void validateUniqueNonBlank(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must not contain blank values");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(name + " must be unique");
        }
    }

    public String getQuery() { return query; }
    public PortfolioTaskMode getMode() { return mode; }
    public PortfolioConditions getConditions() { return conditions; }
    public int getLimit() { return limit; }
    public List<String> getRequiredPortfolioIds() { return requiredPortfolioIds; }
    public List<String> getRequiredClaimIds() { return requiredClaimIds; }
    public boolean isExactPortfolioLookup() { return exactPortfolioLookup; }
    public PortfolioRetrievalStrategy getStrategy() { return strategy; }
    public List<AnswerClaimCategory> getPreferredClaimCategories() {
        return preferredClaimCategories;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalRequest that)) { return false; }
        return Objects.equals(query, that.query) && mode == that.mode
                && Objects.equals(conditions, that.conditions) && limit == that.limit
                && Objects.equals(requiredPortfolioIds, that.requiredPortfolioIds)
                && Objects.equals(requiredClaimIds, that.requiredClaimIds)
                && exactPortfolioLookup == that.exactPortfolioLookup
                && strategy == that.strategy
                && Objects.equals(preferredClaimCategories, that.preferredClaimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, mode, conditions, limit, requiredPortfolioIds,
                requiredClaimIds, exactPortfolioLookup, strategy, preferredClaimCategories);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievalRequest{" + "query='<redacted>'"
                + ", mode=" + mode + ", conditions=" + conditions + ", limit=" + limit
                + ", requiredPortfolioIdCount=" + requiredPortfolioIds.size() + '}';
    }
}
