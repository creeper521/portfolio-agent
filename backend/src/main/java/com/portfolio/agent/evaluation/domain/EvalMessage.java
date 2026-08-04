package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class EvalMessage {

    private final String role;
    private final String content;

    @JsonCreator
    public EvalMessage(@JsonProperty("role") String role,
                       @JsonProperty("content") String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
