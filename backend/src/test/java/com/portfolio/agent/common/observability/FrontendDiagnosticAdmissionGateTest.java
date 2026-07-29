package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendDiagnosticAdmissionGateTest {

    @Test
    void admitsAtMostTheConfiguredNumberOfEventsForOneSourcePerMinute() {
        FrontendDiagnosticAdmissionGate gate = new FrontendDiagnosticAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                30);

        assertThat(gate.tryAdmit("source-a", 10)).isTrue();
        assertThat(gate.tryAdmit("source-a", 10)).isTrue();
        assertThat(gate.tryAdmit("source-a", 10)).isTrue();
        assertThat(gate.tryAdmit("source-a", 1)).isFalse();
    }

    @Test
    void tracksSourcesIndependently() {
        FrontendDiagnosticAdmissionGate gate = new FrontendDiagnosticAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                1);

        assertThat(gate.tryAdmit("source-a", 1)).isTrue();
        assertThat(gate.tryAdmit("source-a", 1)).isFalse();
        assertThat(gate.tryAdmit("source-b", 1)).isTrue();
    }

    @Test
    void opensANewWindowAfterOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
        FrontendDiagnosticAdmissionGate gate =
                new FrontendDiagnosticAdmissionGate(clock, 1);

        assertThat(gate.tryAdmit("source-a", 1)).isTrue();
        assertThat(gate.tryAdmit("source-a", 1)).isFalse();
        clock.advance(Duration.ofMinutes(1));

        assertThat(gate.tryAdmit("source-a", 1)).isTrue();
    }

    @Test
    void rejectsInvalidAdmissionArguments() {
        FrontendDiagnosticAdmissionGate gate = new FrontendDiagnosticAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                30);

        assertThat(gate.tryAdmit("source-a", 0)).isFalse();
        assertThat(gate.tryAdmit("source-a", 31)).isFalse();
        assertThat(gate.tryAdmit(null, 1)).isFalse();
    }

    @Test
    void concurrentAdmissionNeverExceedsTheEventBudget() throws Exception {
        FrontendDiagnosticAdmissionGate gate = new FrontendDiagnosticAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                30);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 200; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return gate.tryAdmit("shared-source", 1);
                }));
            }
            start.countDown();
            int admitted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    admitted++;
                }
            }

            assertThat(admitted).isEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNewSourcesAtCapacityAndRecoversExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
        FrontendDiagnosticAdmissionGate gate =
                new FrontendDiagnosticAdmissionGate(clock, 30);
        for (int index = 0; index < 10_000; index++) {
            assertThat(gate.tryAdmit("source-" + index, 1))
                    .as("source %s", index)
                    .isTrue();
        }

        assertThat(gate.tryAdmit("source-over-capacity", 1)).isFalse();
        clock.advance(Duration.ofMinutes(1));

        assertThat(gate.tryAdmit("source-after-expiry", 1)).isTrue();
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
