package com.portfolio.agent.answer.context.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

public final class OrderedResultSelection {
    private final SubjectOrderKind orderKind;
    private final List<Item> items;
    public OrderedResultSelection(SubjectOrderKind orderKind, List<Item> items) {
        if (orderKind != SubjectOrderKind.RECOMMENDATION_RANK) throw new IllegalArgumentException("recommendation results require recommendation rank");
        if (items == null || items.isEmpty() || items.size() > 5) throw new IllegalArgumentException("selected results must be 1..5");
        this.orderKind = orderKind; this.items = items.stream().sorted(Comparator.comparingInt(Item::getPosition)).toList();
    }
    public SubjectOrderKind getOrderKind() { return orderKind; }
    public List<Item> getItems() { return items; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof OrderedResultSelection that
                && orderKind == that.orderKind && items.equals(that.items);
    }
    @Override public int hashCode() { return Objects.hash(orderKind, items); }
    public static final class Item {
        private final int position; private final String resultItemId; private final String portfolioId;
        private final SemanticRoutingTypes.SubjectType subjectType;
        public Item(int position, String resultItemId, String portfolioId) {
            this(position, resultItemId, portfolioId, SemanticRoutingTypes.SubjectType.PROJECT);
        }
        public Item(int position, String resultItemId, String portfolioId,
                    SemanticRoutingTypes.SubjectType subjectType) {
            if (position < 1 || resultItemId == null || resultItemId.isBlank() || portfolioId == null || portfolioId.isBlank()) throw new IllegalArgumentException("selected result is invalid");
            this.position = position; this.resultItemId = resultItemId.trim(); this.portfolioId = portfolioId.trim();
            if (subjectType == SemanticRoutingTypes.SubjectType.RESULT) throw new IllegalArgumentException("selected result subject must be public");
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        }
        public int getPosition() { return position; }
        public String getResultItemId() { return resultItemId; }
        public String getPortfolioId() { return portfolioId; }
        public SemanticRoutingTypes.SubjectType getSubjectType() { return subjectType; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Item that && position == that.position
                    && resultItemId.equals(that.resultItemId) && portfolioId.equals(that.portfolioId)
                    && subjectType == that.subjectType;
        }
        @Override public int hashCode() { return Objects.hash(position, resultItemId, portfolioId, subjectType); }
    }
}
