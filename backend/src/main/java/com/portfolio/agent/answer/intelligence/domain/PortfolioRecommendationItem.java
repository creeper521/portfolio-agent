package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRecommendationItem {

    private final String portfolioId;
    private final String title;
    private final String route;
    private final List<String> matchReasons;
    private final List<String> evidenceIds;

    public PortfolioRecommendationItem(
            String portfolioId,
            String title,
            String route,
            List<String> matchReasons,
            List<String> evidenceIds) {
        this.portfolioId = requireText(portfolioId, "portfolioId");
        this.title = requireText(title, "title");
        this.route = requireText(route, "route");
        this.matchReasons = List.copyOf(Objects.requireNonNull(matchReasons, "matchReasons"));
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
    }

    public String getPortfolioId() { return portfolioId; }
    public String getTitle() { return title; }
    public String getRoute() { return route; }
    public List<String> getMatchReasons() { return matchReasons; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRecommendationItem that)) { return false; }
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(title, that.title)
                && Objects.equals(route, that.route)
                && Objects.equals(matchReasons, that.matchReasons)
                && Objects.equals(evidenceIds, that.evidenceIds);
    }

    @Override
    public int hashCode() { return Objects.hash(portfolioId, title, route, matchReasons, evidenceIds); }

    @Override
    public String toString() {
        return "PortfolioRecommendationItem{" + "portfolioId='" + portfolioId + '\''
                + ", title='" + title + '\'' + ", route='" + route + '\''
                + ", matchReasonCount=" + matchReasons.size()
                + ", evidenceCount=" + evidenceIds.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }
}
