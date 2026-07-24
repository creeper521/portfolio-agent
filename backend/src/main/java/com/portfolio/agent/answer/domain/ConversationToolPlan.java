package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ConversationToolPlan {

    private final List<ToolCall> calls;

    @JsonCreator
    public ConversationToolPlan(@JsonProperty("calls") List<ToolCall> calls) {
        this.calls = calls == null ? List.of() : List.copyOf(calls);
    }

    public List<ToolCall> getCalls() {
        return calls;
    }
}
