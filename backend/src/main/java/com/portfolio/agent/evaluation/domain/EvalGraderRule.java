package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class EvalGraderRule {

    private final String type;
    private final EvalSeverity severity;

    @JsonCreator
    public EvalGraderRule(@JsonProperty("type") String type,
                          @JsonProperty("severity") EvalSeverity severity) {
        this.type = type;
        this.severity = severity;
    }

    public String getType() {
        return type;
    }

    public EvalSeverity getSeverity() {
        return severity;
    }
}
