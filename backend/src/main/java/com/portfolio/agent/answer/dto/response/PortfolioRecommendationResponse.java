package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;

import java.util.List;
import java.util.Objects;

public final class PortfolioRecommendationResponse {

    private final String recommendationBatchId;
    private final PortfolioRecommendationContextResponse context;
    private final List<PortfolioRecommendationItemResponse> items;
    private final List<String> satisfiedConstraints;
    private final List<String> unsatisfiedConstraints;

    public PortfolioRecommendationResponse(
            String recommendationBatchId,
            PortfolioRecommendationContextResponse context,
            List<PortfolioRecommendationItemResponse> items,
            List<String> satisfiedConstraints,
            List<String> unsatisfiedConstraints) {
        this.recommendationBatchId = Objects.requireNonNull(recommendationBatchId, "recommendationBatchId");
        this.context = Objects.requireNonNull(context, "context");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.satisfiedConstraints = List.copyOf(
                Objects.requireNonNull(satisfiedConstraints, "satisfiedConstraints"));
        this.unsatisfiedConstraints = List.copyOf(
                Objects.requireNonNull(unsatisfiedConstraints, "unsatisfiedConstraints"));
    }

    public static PortfolioRecommendationResponse from(PortfolioRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation");
        return new PortfolioRecommendationResponse(
                recommendation.getRecommendationBatchId(),
                PortfolioRecommendationContextResponse.from(recommendation.getContext()),
                recommendation.getItems().stream().map(PortfolioRecommendationItemResponse::from).toList(),
                recommendation.getSatisfiedConstraints(),
                recommendation.getUnsatisfiedConstraints());
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public PortfolioRecommendationContextResponse getContext() { return context; }
    public List<PortfolioRecommendationItemResponse> getItems() { return items; }
    public List<String> getSatisfiedConstraints() { return satisfiedConstraints; }
    public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }
}
