package com.portfolio.agent.answer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousSourceHasherTest {
    @Test
    void createsStableNonReversibleHexDigest() {
        AnonymousSourceHasher hasher = new AnonymousSourceHasher(
                "01234567890123456789012345678901".getBytes());
        String first = hasher.hash("203.0.113.7");
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("203.0.113.7")).isEqualTo(first);
        assertThat(first).doesNotContain("203.0.113.7");
        assertThat(hasher.hash("203.0.113.8")).isNotEqualTo(first);
    }
}
