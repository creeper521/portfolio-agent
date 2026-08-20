package com.portfolio.agent.release.benchmark.selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public final class PortfolioSelectionObservationSet {
    private final String releaseVersion;
    private final List<PortfolioSelectionObservation> observations;

    @JsonCreator
    public PortfolioSelectionObservationSet(
            @JsonProperty("releaseVersion") String releaseVersion,
            @JsonProperty("observations") List<PortfolioSelectionObservation> observations) {
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
    }

    public String getReleaseVersion() { return releaseVersion; }
    public List<PortfolioSelectionObservation> getObservations() { return observations; }
}
