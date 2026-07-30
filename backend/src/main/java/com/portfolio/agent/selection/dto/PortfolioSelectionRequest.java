package com.portfolio.agent.selection.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public final class PortfolioSelectionRequest {

    private final String careerTrack;
    @NotNull
    private final AudienceRole audienceRole;
    private final Set<String> capabilityCodes;
    private final String goal;
    @Min(2)
    @Max(5)
    private final Integer requestedSize;

    @JsonCreator
    public PortfolioSelectionRequest(
            @JsonProperty("careerTrack") String careerTrack,
            @JsonProperty("audienceRole") AudienceRole audienceRole,
            @JsonProperty("capabilityCodes") Set<@NotBlank String> capabilityCodes,
            @JsonProperty("goal") String goal,
            @JsonProperty("requestedSize") Integer requestedSize) {
        this.careerTrack = careerTrack;
        this.audienceRole = audienceRole;
        this.capabilityCodes = capabilityCodes == null ? Set.of() : Set.copyOf(capabilityCodes);
        this.goal = goal;
        this.requestedSize = requestedSize;
    }

    public String getCareerTrack() {
        return careerTrack;
    }

    public AudienceRole getAudienceRole() {
        return audienceRole;
    }

    public Set<String> getCapabilityCodes() {
        return capabilityCodes;
    }

    public String getGoal() {
        return goal;
    }

    public int resolvedRequestedSize() {
        return requestedSize == null ? 3 : requestedSize;
    }
}
