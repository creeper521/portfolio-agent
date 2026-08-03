package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;

import java.util.List;
import java.util.Objects;

public final class PortfolioRecommendationItemResponse {

    private final String portfolioId;
    private final String title;
    private final String route;
    private final List<String> matchReasons;
    private final List<String> evidenceIds;

    public PortfolioRecommendationItemResponse(
            String portfolioId,
            String title,
            String route,
            List<String> matchReasons,
            List<String> evidenceIds) {
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId");
        this.title = Objects.requireNonNull(title, "title");
        this.route = Objects.requireNonNull(route, "route");
        this.matchReasons = List.copyOf(Objects.requireNonNull(matchReasons, "matchReasons"));
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
    }

    public static PortfolioRecommendationItemResponse from(PortfolioRecommendationItem item) {
        Objects.requireNonNull(item, "item");
        return new PortfolioRecommendationItemResponse(
                item.getPortfolioId(), item.getTitle(), item.getRoute(),
                item.getMatchReasons(), item.getEvidenceIds());
    }

    public String getPortfolioId() { return portfolioId; }
    public String getTitle() { return title; }
    public String getRoute() { return route; }
    public List<String> getMatchReasons() { return matchReasons; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
