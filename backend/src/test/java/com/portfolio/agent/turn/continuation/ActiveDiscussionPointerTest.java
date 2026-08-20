package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveDiscussionPointerTest {

    @Test
    void derivesActiveAndExpiredFromTimeWithoutPersistingASecondStatus() {
        Instant expiresAt = Instant.parse("2026-08-20T08:30:00Z");
        ActiveDiscussionPointer pointer = new ActiveDiscussionPointer(
                "discussion_handle_123", "project-a", expiresAt);

        assertThat(pointer.statusAt(
                Instant.parse("2026-08-20T08:29:59Z")))
                .isEqualTo(ActiveDiscussionPointer.Status.ACTIVE);
        assertThat(pointer.statusAt(expiresAt))
                .isEqualTo(ActiveDiscussionPointer.Status.EXPIRED);
    }

    @Test
    void handleIsThePointerGeneration() {
        ActiveDiscussionPointer pointer = new ActiveDiscussionPointer(
                "discussion_handle_123",
                "project-a",
                Instant.parse("2026-08-20T08:30:00Z"));

        assertThat(pointer.matchesGeneration("discussion_handle_123")).isTrue();
        assertThat(pointer.matchesGeneration("discussion_handle_456")).isFalse();
    }
}
