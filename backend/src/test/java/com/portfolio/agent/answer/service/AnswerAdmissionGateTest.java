package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.exception.AnswerAdmissionRejectedException;
import com.portfolio.agent.answer.exception.AnswerErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerAdmissionGateTest {

    @Test
    void rejectsTheEleventhRequestInsideTheSameMinute() {
        var clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        var gate = new AnswerAdmissionGate(clock, 10, 2);

        for (int index = 0; index < 10; index++) {
            gate.acquire("source-a", UUID.randomUUID()).close();
        }

        assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
                .isInstanceOfSatisfying(AnswerAdmissionRejectedException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AnswerErrorCode.ANSWER_RATE_LIMITED);
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(60);
                });
    }

    @Test
    void startsANewRequestWindowAfterSixtySeconds() {
        var clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        var gate = new AnswerAdmissionGate(clock, 1, 2);

        gate.acquire("source-a", UUID.randomUUID()).close();
        clock.advance(Duration.ofSeconds(60));

        try (var ignored = gate.acquire("source-a", UUID.randomUUID())) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void rejectsMoreThanTheConfiguredConcurrentRequests() {
        var gate = new AnswerAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                10,
                2
        );
        var first = gate.acquire("source-a", UUID.randomUUID());
        var second = gate.acquire("source-a", UUID.randomUUID());

        assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
                .isInstanceOfSatisfying(AnswerAdmissionRejectedException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AnswerErrorCode.ANSWER_CONCURRENCY_LIMITED);
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(1);
                });

        first.close();
        second.close();
    }

    @Test
    void releasingAnAdmissionRestoresConcurrentCapacity() {
        var gate = new AnswerAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                10,
                1
        );
        var first = gate.acquire("source-a", UUID.randomUUID());

        first.close();
        first.close();

        try (var ignored = gate.acquire("source-a", UUID.randomUUID())) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void tracksDifferentSourcesIndependently() {
        var gate = new AnswerAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                1,
                1
        );
        gate.acquire("source-a", UUID.randomUUID()).close();

        try (var ignored = gate.acquire("source-b", UUID.randomUUID())) {
            assertThat(ignored).isNotNull();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
