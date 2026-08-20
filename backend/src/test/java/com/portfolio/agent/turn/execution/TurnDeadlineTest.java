package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TurnDeadlineTest {

    @Test
    void derivedDeadlinesNeverExtendTheOriginalAbsoluteBudget() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        TurnDeadline turn = TurnDeadline.after(Duration.ofSeconds(20), clock);

        assertThat(turn.minus(Duration.ofSeconds(2)).getExpiresAt())
                .isEqualTo(Instant.parse("2026-08-19T00:00:18Z"));
        assertThat(turn.cappedAt(Duration.ofSeconds(8)).getExpiresAt())
                .isEqualTo(Instant.parse("2026-08-19T00:00:08Z"));
        assertThat(turn.cappedAt(Duration.ofSeconds(30)).getExpiresAt())
                .isEqualTo(turn.getExpiresAt());
    }
}
