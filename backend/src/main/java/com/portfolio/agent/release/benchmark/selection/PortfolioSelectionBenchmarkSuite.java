package com.portfolio.agent.release.benchmark.selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public final class PortfolioSelectionBenchmarkSuite {
    private final String releaseVersion;
    private final List<PortfolioSelectionBenchmarkCase> cases;

    @JsonCreator
    public PortfolioSelectionBenchmarkSuite(
            @JsonProperty("releaseVersion") String releaseVersion,
            @JsonProperty("cases") List<PortfolioSelectionBenchmarkCase> cases) {
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
    }

    public String getReleaseVersion() { return releaseVersion; }
    public List<PortfolioSelectionBenchmarkCase> getCases() { return cases; }
}
