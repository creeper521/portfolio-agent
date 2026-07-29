package com.portfolio.agent.common.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class FrontendDiagnosticBatchRequest {

    @NotNull
    @Size(min = 1, max = 10)
    private final List<@Valid FrontendDiagnosticEventRequest> events;

    @JsonCreator
    public FrontendDiagnosticBatchRequest(
            @JsonProperty("events") List<FrontendDiagnosticEventRequest> events
    ) {
        this.events = events == null ? null : List.copyOf(events);
    }

    public List<FrontendDiagnosticEventRequest> getEvents() {
        return events;
    }
}
