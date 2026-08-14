package com.portfolio.agent.answer.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerIdempotencyCoordinatorTest {

    @Test
    void concurrentDuplicateRequestsShareOneExecution() throws Exception {
        AnswerIdempotencyCoordinator<String> coordinator = new AnswerIdempotencyCoordinator<String>(
                Clock.systemUTC(), Duration.ofMinutes(2));
        UUID token = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> coordinator.execute("source", token, () -> {
                executions.incrementAndGet();
                started.countDown();
                await(release);
                return "answer";
            }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            Future<String> duplicate = executor.submit(
                    () -> coordinator.execute("source", token, () -> "duplicate"));

            release.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("answer");
            assertThat(duplicate.get(1, TimeUnit.SECONDS)).isEqualTo("answer");
            assertThat(executions).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reusesACompletedResultUntilItsTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        AnswerIdempotencyCoordinator<String> coordinator = new AnswerIdempotencyCoordinator<String>(
                clock, Duration.ofMinutes(2));
        UUID token = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();

        assertThat(coordinator.execute(
                "source", token, () -> "answer-" + executions.incrementAndGet()))
                .isEqualTo("answer-1");
        assertThat(coordinator.execute(
                "source", token, () -> "answer-" + executions.incrementAndGet()))
                .isEqualTo("answer-1");

        clock.advance(Duration.ofMinutes(2));

        assertThat(coordinator.execute(
                "source", token, () -> "answer-" + executions.incrementAndGet()))
                .isEqualTo("answer-2");
    }

    @Test
    void rejectsAReusedTokenWhenTheRequestFingerprintChanges() {
        AnswerIdempotencyCoordinator<String> coordinator = new AnswerIdempotencyCoordinator<String>(
                Clock.systemUTC(), Duration.ofMinutes(2));
        UUID token = UUID.randomUUID();

        assertThat(coordinator.execute("source", token, "fingerprint-one", () -> "answer"))
                .isEqualTo("answer");

        assertThatThrownBy(() -> coordinator.execute(
                "source", token, "fingerprint-two", () -> "different"))
                .isInstanceOf(RequestReceiptConflictException.class);
    }

    @Test
    void removesExpiredEntriesGloballyAndBoundsCompletedResults() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        AnswerIdempotencyCoordinator<String> coordinator = new AnswerIdempotencyCoordinator<String>(
                clock, Duration.ofMinutes(2), 2);
        coordinator.execute("source", UUID.randomUUID(), () -> "one");
        coordinator.execute("source", UUID.randomUUID(), () -> "two");
        assertThat(coordinator.entryCount()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(2));
        coordinator.execute("source", UUID.randomUUID(), () -> "three");

        assertThat(coordinator.entryCount()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
