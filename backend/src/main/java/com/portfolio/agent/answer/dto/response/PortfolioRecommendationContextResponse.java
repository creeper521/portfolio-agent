package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioRecommendationContextResponse {

    private final String recommendationBatchId;
    private final String contentVersion;
    private final String careerTrack;
    private final String audienceRole;
    private final Set<String> capabilityCodes;
    private final int requestedSize;
    private final List<String> selectedPortfolioIds;

    public PortfolioRecommendationContextResponse(
            String recommendationBatchId,
            String contentVersion,
            String careerTrack,
            String audienceRole,
            Set<String> capabilityCodes,
            int requestedSize,
            List<String> selectedPortfolioIds) {
        this.recommendationBatchId = Objects.requireNonNull(recommendationBatchId, "recommendationBatchId");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.careerTrack = careerTrack;
        this.audienceRole = Objects.requireNonNull(audienceRole, "audienceRole");
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        this.requestedSize = requestedSize;
        this.selectedPortfolioIds = List.copyOf(
                Objects.requireNonNull(selectedPortfolioIds, "selectedPortfolioIds"));
    }

    public static PortfolioRecommendationContextResponse from(PortfolioRecommendationContext context) {
        Objects.requireNonNull(context, "context");
        return new PortfolioRecommendationContextResponse(
                context.getRecommendationBatchId(),
                context.getContentVersion(),
                context.getCareerTrack(),
                context.getAudienceRole(),
                context.getCapabilityCodes(),
                context.getRequestedSize(),
                context.getSelectedPortfolioIds());
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public String getContentVersion() { return contentVersion; }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getCareerTrack() { return careerTrack; }
    public String getAudienceRole() { return audienceRole; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public int getRequestedSize() { return requestedSize; }
    public List<String> getSelectedPortfolioIds() { return selectedPortfolioIds; }
}
