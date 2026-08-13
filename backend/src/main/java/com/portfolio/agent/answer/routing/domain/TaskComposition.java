package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.composition.domain.CompositionMode;
import java.util.Objects;

/** Public-safe task composition metadata; it contains no provider or failure detail. */
public final class TaskComposition {
    private final CompositionMode mode;
    private final boolean degraded;

    public TaskComposition(CompositionMode mode, boolean degraded) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.degraded = degraded;
    }

    public CompositionMode getMode() { return mode; }
    public boolean isDegraded() { return degraded; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TaskComposition that)) return false;
        return degraded == that.degraded && mode == that.mode;
    }

    @Override
    public int hashCode() { return Objects.hash(mode, degraded); }

    @Override
    public String toString() { return "TaskComposition{mode=" + mode + ", degraded=" + degraded + '}'; }
}
