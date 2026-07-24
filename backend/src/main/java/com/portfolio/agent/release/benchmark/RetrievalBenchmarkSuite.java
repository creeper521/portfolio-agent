package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class RetrievalBenchmarkSuite {

    private final String suiteVersion;
    private final String contentVersion;
    private final List<RetrievalBenchmarkCase> cases;

    @JsonCreator
    public RetrievalBenchmarkSuite(
            @JsonProperty("suiteVersion") String suiteVersion,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("cases") List<RetrievalBenchmarkCase> cases
    ) {
        this.suiteVersion = suiteVersion;
        this.contentVersion = contentVersion;
        this.cases = List.copyOf(cases);
    }

    public String getSuiteVersion() { return suiteVersion; }
    public String getContentVersion() { return contentVersion; }
    public List<RetrievalBenchmarkCase> getCases() { return cases; }
}
