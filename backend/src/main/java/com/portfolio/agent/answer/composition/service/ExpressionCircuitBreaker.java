package com.portfolio.agent.answer.composition.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Process-local, non-blocking breaker for eligible expression attempts. */
public final class ExpressionCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);
    private final Clock clock;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean halfOpenProbeInFlight;

    public ExpressionCircuitBreaker() { this(Clock.systemUTC()); }
    public ExpressionCircuitBreaker(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public synchronized boolean tryAcquire() {
        Instant now = Instant.now(clock);
        if (state == State.CLOSED) return true;
        if (state == State.OPEN) {
            if (openedAt == null || now.isBefore(openedAt.plus(OPEN_DURATION))) return false;
            state = State.HALF_OPEN;
            halfOpenProbeInFlight = false;
        }
        if (state == State.HALF_OPEN && !halfOpenProbeInFlight) {
            halfOpenProbeInFlight = true;
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
        halfOpenProbeInFlight = false;
    }

    public synchronized void recordEligibleFailure() {
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            openedAt = Instant.now(clock);
            halfOpenProbeInFlight = false;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            state = State.OPEN;
            openedAt = Instant.now(clock);
        }
    }

    /** Ends a half-open probe for a local-only failure without crediting provider success. */
    public synchronized void recordNeutralCompletion() {
        if (state == State.HALF_OPEN && halfOpenProbeInFlight) {
            state = State.OPEN;
            openedAt = Instant.now(clock);
            halfOpenProbeInFlight = false;
        }
    }

    public synchronized State getState() {
        if (state == State.OPEN && openedAt != null
                && !Instant.now(clock).isBefore(openedAt.plus(OPEN_DURATION))) {
            return State.HALF_OPEN;
        }
        return state;
    }
    public synchronized int getConsecutiveFailures() { return consecutiveFailures; }
}
