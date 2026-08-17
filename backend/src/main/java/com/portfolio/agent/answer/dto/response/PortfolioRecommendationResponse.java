package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

public final class PortfolioRecommendationResponse {

    private final String recommendationBatchId;
    private final List<PortfolioRecommendationItemResponse> items;
    private final List<String> satisfiedConstraints;
    private final List<String> unsatisfiedConstraints;
    private final int requestedSize;
    private final int actualSize;
    private final String candidateScope;
    private final List<String> selectedPortfolioIds;
    private final List<String> reasonCodes;

    public PortfolioRecommendationResponse(
            String recommendationBatchId,
            List<PortfolioRecommendationItemResponse> items,
            List<String> satisfiedConstraints,
            List<String> unsatisfiedConstraints) {
        this(recommendationBatchId, items, satisfiedConstraints, unsatisfiedConstraints,
                items.size(), items.size(), "EXPLICIT_PROJECT_SET",
                items.stream().map(PortfolioRecommendationItemResponse::getPortfolioId).toList(), List.of());
    }

    public PortfolioRecommendationResponse(
            String recommendationBatchId, List<PortfolioRecommendationItemResponse> items,
            List<String> satisfiedConstraints, List<String> unsatisfiedConstraints,
            int requestedSize, int actualSize, String candidateScope,
            List<String> selectedPortfolioIds, List<String> reasonCodes) {
        this.recommendationBatchId = Objects.requireNonNull(recommendationBatchId, "recommendationBatchId");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.satisfiedConstraints = List.copyOf(
                Objects.requireNonNull(satisfiedConstraints, "satisfiedConstraints"));
        this.unsatisfiedConstraints = List.copyOf(
                Objects.requireNonNull(unsatisfiedConstraints, "unsatisfiedConstraints"));
        if (requestedSize < 0 || actualSize != this.items.size()) {
            throw new IllegalArgumentException("recommendation size is invalid");
        }
        this.requestedSize = requestedSize;
        this.actualSize = actualSize;
        this.candidateScope = requestedSize == 0 ? null
                : Objects.requireNonNull(candidateScope, "candidateScope");
        this.selectedPortfolioIds = List.copyOf(Objects.requireNonNull(selectedPortfolioIds, "selectedPortfolioIds"));
        this.reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
    }

    public static PortfolioRecommendationResponse from(PortfolioRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation");
        return new PortfolioRecommendationResponse(
                recommendation.getRecommendationBatchId(),
                recommendation.getItems().stream().map(PortfolioRecommendationItemResponse::from).toList(),
                recommendation.getSatisfiedConstraints(),
                recommendation.getUnsatisfiedConstraints());
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public List<PortfolioRecommendationItemResponse> getItems() { return items; }
    public List<String> getSatisfiedConstraints() { return satisfiedConstraints; }
    public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int getRequestedSize() { return requestedSize; }
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int getActualSize() { return actualSize; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCandidateScope() { return candidateScope; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getSelectedPortfolioIds() { return selectedPortfolioIds; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getReasonCodes() { return reasonCodes; }
}
