package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Set;

public final class PortfolioRecommendationContextRequest {

    @NotBlank(message = "recommendationBatchId is required")
    @Pattern(regexp = "rec_[0-9a-f]{64}", message = "recommendationBatchId format is invalid")
    private final String recommendationBatchId;

    @NotBlank(message = "contentVersion is required")
    private final String contentVersion;

    private final String careerTrack;

    @NotBlank(message = "audienceRole is required")
    private final String audienceRole;

    @NotNull(message = "capabilityCodes is required")
    private final Set<@NotBlank(message = "capabilityCodes must not contain blank values") String> capabilityCodes;

    @NotNull(message = "requestedSize is required")
    @Min(value = 2, message = "requestedSize must be between 2 and 5")
    @Max(value = 5, message = "requestedSize must be between 2 and 5")
    private final Integer requestedSize;

    @NotNull(message = "selectedPortfolioIds is required")
    private final List<@NotBlank(message = "selectedPortfolioIds must not contain blank values") String> selectedPortfolioIds;

    @JsonCreator
    public PortfolioRecommendationContextRequest(
            @JsonProperty("recommendationBatchId") String recommendationBatchId,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("careerTrack") String careerTrack,
            @JsonProperty("audienceRole") String audienceRole,
            @JsonProperty("capabilityCodes") Set<String> capabilityCodes,
            @JsonProperty("requestedSize") Integer requestedSize,
            @JsonProperty("selectedPortfolioIds") List<String> selectedPortfolioIds) {
        this.recommendationBatchId = recommendationBatchId;
        this.contentVersion = contentVersion;
        this.careerTrack = careerTrack;
        this.audienceRole = audienceRole;
        this.capabilityCodes = capabilityCodes == null ? null : Set.copyOf(capabilityCodes);
        this.requestedSize = requestedSize;
        this.selectedPortfolioIds = selectedPortfolioIds == null ? null : List.copyOf(selectedPortfolioIds);
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public String getContentVersion() { return contentVersion; }
    public String getCareerTrack() { return careerTrack; }
    public String getAudienceRole() { return audienceRole; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public Integer getRequestedSize() { return requestedSize; }
    public List<String> getSelectedPortfolioIds() { return selectedPortfolioIds; }
}
