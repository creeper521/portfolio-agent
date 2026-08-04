package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class EvalSuite {

    private final String schemaVersion;
    private final String suiteId;
    private final String datasetVersion;
    private final List<EvalCase> cases;

    @JsonCreator
    public EvalSuite(@JsonProperty("schemaVersion") String schemaVersion,
                     @JsonProperty("suiteId") String suiteId,
                     @JsonProperty("datasetVersion") String datasetVersion,
                     @JsonProperty("cases") List<EvalCase> cases) {
        this.schemaVersion = schemaVersion;
        this.suiteId = suiteId;
        this.datasetVersion = datasetVersion;
        this.cases = cases == null ? null : List.copyOf(cases);
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public List<EvalCase> getCases() {
        return cases;
    }

    public EvalSuite withCases(List<EvalCase> sortedCases) {
        return new EvalSuite(schemaVersion, suiteId, datasetVersion, sortedCases);
    }
}
