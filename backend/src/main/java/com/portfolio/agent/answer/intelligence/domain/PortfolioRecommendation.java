package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRecommendation {

    private final String recommendationBatchId;
    private final PortfolioRecommendationContext context;
    private final List<PortfolioRecommendationItem> items;
    private final List<String> satisfiedConstraints;
    private final List<String> unsatisfiedConstraints;

    public PortfolioRecommendation(
            String recommendationBatchId,
            PortfolioRecommendationContext context,
            List<PortfolioRecommendationItem> items,
            List<String> satisfiedConstraints,
            List<String> unsatisfiedConstraints) {
        this.recommendationBatchId = requireText(recommendationBatchId, "recommendationBatchId");
        this.context = Objects.requireNonNull(context, "context");
        if (!recommendationBatchId.equals(context.getRecommendationBatchId())) {
            throw new IllegalArgumentException("recommendationBatchId must match context");
        }
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.satisfiedConstraints = List.copyOf(
                Objects.requireNonNull(satisfiedConstraints, "satisfiedConstraints"));
        this.unsatisfiedConstraints = List.copyOf(
                Objects.requireNonNull(unsatisfiedConstraints, "unsatisfiedConstraints"));
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public PortfolioRecommendationContext getContext() { return context; }
    public List<PortfolioRecommendationItem> getItems() { return items; }
    public List<String> getSatisfiedConstraints() { return satisfiedConstraints; }
    public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRecommendation that)) { return false; }
        return Objects.equals(recommendationBatchId, that.recommendationBatchId)
                && Objects.equals(context, that.context)
                && Objects.equals(items, that.items)
                && Objects.equals(satisfiedConstraints, that.satisfiedConstraints)
                && Objects.equals(unsatisfiedConstraints, that.unsatisfiedConstraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recommendationBatchId, context, items,
                satisfiedConstraints, unsatisfiedConstraints);
    }

    @Override
    public String toString() {
        return "PortfolioRecommendation{" + "itemCount=" + items.size()
                + ", satisfiedConstraintCount=" + satisfiedConstraints.size()
                + ", unsatisfiedConstraintCount=" + unsatisfiedConstraints.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }
}
