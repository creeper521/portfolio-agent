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

    /**
     * 从同一绝对时钟派生更早的截止点，用于给结算预留时间。
     * 派生不会创建新的时间预算，也不会把已经消耗的时间补回来。
     */
    public TurnDeadline minus(Duration reserve) {
        if (reserve == null || reserve.isNegative()) {
            throw new IllegalArgumentException("reserve must not be negative");
        }
        return new TurnDeadline(expiresAt.minus(reserve), clock);
    }

    /**
     * 将单次操作限制在当前 Turn 剩余预算内，避免子操作重新起算超时。
     */
    public TurnDeadline cappedAt(Duration maximumDuration) {
        if (maximumDuration == null || maximumDuration.isNegative()
                || maximumDuration.isZero()) {
            throw new IllegalArgumentException("maximumDuration must be positive");
        }
        Instant operationExpiry = clock.instant().plus(maximumDuration);
        return new TurnDeadline(
                operationExpiry.isBefore(expiresAt) ? operationExpiry : expiresAt,
                clock);
    }

    public boolean isExpired() { return !clock.instant().isBefore(expiresAt); }
    public long remainingMillis() {
        return Math.max(0L, Duration.between(clock.instant(), expiresAt).toMillis());
    }
    public Instant getExpiresAt() { return expiresAt; }
}
