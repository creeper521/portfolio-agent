package com.portfolio.agent.answer.composition.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExpressionCircuitBreakerTest {
    @Test void opensAfterThreeEligibleFailuresAndAllowsOneProbe() {
        Instant start = Instant.parse("2026-08-13T00:00:00Z");
        ExpressionCircuitBreaker breaker = new ExpressionCircuitBreaker(Clock.fixed(start, ZoneOffset.UTC));
        assertThat(breaker.tryAcquire()).isTrue(); breaker.recordEligibleFailure();
        assertThat(breaker.tryAcquire()).isTrue(); breaker.recordEligibleFailure();
        assertThat(breaker.tryAcquire()).isTrue(); breaker.recordEligibleFailure();
        assertThat(breaker.getState()).isEqualTo(ExpressionCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
    }
    @Test void successfulHalfOpenProbeClosesBreaker() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        ExpressionCircuitBreaker breaker = new ExpressionCircuitBreaker(clock);
        for (int i = 0; i < 3; i++) { assertThat(breaker.tryAcquire()).isTrue(); breaker.recordEligibleFailure(); }
        clock.advanceSeconds(30);
        assertThat(breaker.tryAcquire()).isTrue(); assertThat(breaker.tryAcquire()).isFalse(); breaker.recordSuccess();
        assertThat(breaker.getState()).isEqualTo(ExpressionCircuitBreaker.State.CLOSED);
    }
    @Test void failedHalfOpenProbeReopensForAnotherFullWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        ExpressionCircuitBreaker breaker = new ExpressionCircuitBreaker(clock);
        for (int i = 0; i < 3; i++) { breaker.tryAcquire(); breaker.recordEligibleFailure(); }
        clock.advanceSeconds(30);
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.recordEligibleFailure();
        assertThat(breaker.getState()).isEqualTo(ExpressionCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
        clock.advanceSeconds(30);
        assertThat(breaker.tryAcquire()).isTrue();
    }
    @Test void neutralHalfOpenCompletionReopensWithoutCreditingSuccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        ExpressionCircuitBreaker breaker = new ExpressionCircuitBreaker(clock);
        for (int i = 0; i < 3; i++) { breaker.tryAcquire(); breaker.recordEligibleFailure(); }
        clock.advanceSeconds(30);
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.recordNeutralCompletion();
        assertThat(breaker.getState()).isEqualTo(ExpressionCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
    }
    private static final class MutableClock extends Clock { private Instant instant; MutableClock(Instant instant){this.instant=instant;} void advanceSeconds(long seconds){instant=instant.plusSeconds(seconds);} public ZoneOffset getZone(){return ZoneOffset.UTC;} public Clock withZone(java.time.ZoneId zone){return this;} public Instant instant(){return instant;} }
}
