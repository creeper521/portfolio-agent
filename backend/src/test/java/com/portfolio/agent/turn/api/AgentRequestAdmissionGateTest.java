package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.lifecycle.AgentAdmissionRejectedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRequestAdmissionGateTest {

    @Test
    void rejectsTheEleventhRequestInsideOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 10, 2, 10_000);

        for (int index = 0; index < 10; index++) {
            gate.acquire("source-a", UUID.randomUUID()).close();
        }

        assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
                .isInstanceOfSatisfying(AgentAdmissionRejectedException.class, rejection -> {
                    assertThat(rejection.getReason())
                            .isEqualTo(AgentAdmissionRejectedException.RejectionReason.SOURCE_RPM_LIMIT);
                    assertThat(rejection.getRetryAfterSeconds()).isEqualTo(60);
                });
    }

    @Test
    void retryDelayComesFromTheActiveWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 1, 2, 10_000);
        gate.acquire("source-a", UUID.randomUUID()).close();

        clock.advance(Duration.ofSeconds(55));

        assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
                .isInstanceOfSatisfying(AgentAdmissionRejectedException.class, rejection ->
                        assertThat(rejection.getRetryAfterSeconds()).isEqualTo(5));
    }

    @Test
    void thirdConcurrentRequestFromOneSourceIsRejectedAndReleaseRestoresCapacity() {
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC),
                10, 2, 10_000);
        AgentRequestAdmission first = gate.acquire("source-a", UUID.randomUUID());
        AgentRequestAdmission second = gate.acquire("source-a", UUID.randomUUID());

        assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
                .isInstanceOfSatisfying(AgentAdmissionRejectedException.class, rejection -> {
                    assertThat(rejection.getReason()).isEqualTo(
                            AgentAdmissionRejectedException.RejectionReason.SOURCE_CONCURRENCY_LIMIT);
                    assertThat(rejection.getRetryAfterSeconds()).isEqualTo(1);
                });

        first.close();
        first.close();
        try (AgentRequestAdmission ignored = gate.acquire("source-a", UUID.randomUUID())) {
            assertThat(ignored).isNotNull();
        }
        second.close();
    }

    @Test
    void tracksSourcesIndependentlyAndEvictsExpiredInactiveSources() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 1, 1, 2);
        gate.acquire("source-a", UUID.randomUUID()).close();
        gate.acquire("source-b", UUID.randomUUID()).close();

        clock.advance(Duration.ofMinutes(1));
        gate.acquire("source-c", UUID.randomUUID()).close();

        assertThat(gate.trackedSourceCount()).isEqualTo(1);
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
