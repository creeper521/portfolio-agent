package com.portfolio.agent.turn.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class TurnDeadline {
    private final Instant expiresAt;
    private final Clock clock;

    public TurnDeadline(Instant expiresAt, Clock clock) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static TurnDeadline after(Duration duration, Clock clock) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return new TurnDeadline(clock.instant().plus(duration), clock);
    }

    public boolean isExpired() { return !clock.instant().isBefore(expiresAt); }
    public long remainingMillis() {
        return Math.max(0L, Duration.between(clock.instant(), expiresAt).toMillis());
    }
    public Instant getExpiresAt() { return expiresAt; }
}
