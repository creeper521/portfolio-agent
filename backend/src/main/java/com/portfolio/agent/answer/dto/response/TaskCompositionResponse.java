package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.composition.domain.CompositionMode;
import java.util.Objects;

/** Small public contract; internal provider and validation details never cross this boundary. */
public final class TaskCompositionResponse {
    private final CompositionMode mode;
    private final boolean degraded;

    public TaskCompositionResponse(CompositionMode mode, boolean degraded) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.degraded = degraded;
    }

    public CompositionMode getMode() { return mode; }
    public boolean isDegraded() { return degraded; }
}
