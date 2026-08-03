package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.exception.AnswerAdmissionRejectedException;
import com.portfolio.agent.answer.exception.AnswerErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AnswerAdmissionGate {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(1);
    private static final int CLEANUP_BATCH_SIZE = 256;
    private static final int DEFAULT_MAX_TRACKED_SOURCES = 10_000;

    private final Clock clock;
    private final int requestsPerMinute;
    private final int maxConcurrent;
    private final int maxTrackedSources;
    private final Map<String, SourceState> states = new HashMap<>();
    private final Deque<SourceExpiry> expiries = new ArrayDeque<>();
    private Instant nextCleanupAt = Instant.MIN;

    public AnswerAdmissionGate(Clock clock, int requestsPerMinute, int maxConcurrent) {
        this(clock, requestsPerMinute, maxConcurrent, DEFAULT_MAX_TRACKED_SOURCES);
    }

    AnswerAdmissionGate(
            Clock clock,
            int requestsPerMinute,
            int maxConcurrent,
            int maxTrackedSources
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be positive");
        }
        if (maxTrackedSources < 1) {
            throw new IllegalArgumentException("maxTrackedSources must be positive");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.maxConcurrent = maxConcurrent;
        this.maxTrackedSources = maxTrackedSources;
    }

    public AnswerAdmission acquire(String sourceHash, UUID requestToken) {
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        Objects.requireNonNull(requestToken, "requestToken must not be null");
        Instant now = clock.instant();

        synchronized (states) {
            cleanupExpired(now, false);
            if (!states.containsKey(sourceHash) && states.size() >= maxTrackedSources) {
                cleanupExpired(now, true);
            }
            if (!states.containsKey(sourceHash) && states.size() >= maxTrackedSources) {
                throw new AnswerAdmissionRejectedException(
                        AnswerErrorCode.ANSWER_RATE_LIMITED, 60);
            }
            SourceState state = states.get(sourceHash);
            if (state == null) {
                state = new SourceState(now);
                states.put(sourceHash, state);
                expiries.addLast(new SourceExpiry(sourceHash, now.plus(WINDOW)));
            } else if (state.resetWindowIfExpired(now)) {
                expiries.addLast(new SourceExpiry(sourceHash, now.plus(WINDOW)));
            }

            if (state.requests >= requestsPerMinute) {
                throw new AnswerAdmissionRejectedException(
                        AnswerErrorCode.ANSWER_RATE_LIMITED,
                        secondsUntilWindowReset(state.windowStartedAt, now)
                );
            }
            if (state.active >= maxConcurrent) {
                throw new AnswerAdmissionRejectedException(
                        AnswerErrorCode.ANSWER_CONCURRENCY_LIMITED,
                        1
                );
            }

            state.requests++;
            state.active++;
        }

        return new AnswerAdmission(() -> release(sourceHash));
    }

    private void cleanupExpired(Instant now, boolean force) {
        if (!force && now.isBefore(nextCleanupAt)) {
            return;
        }
        int inspected = 0;
        while (!expiries.isEmpty()
                && !now.isBefore(expiries.peekFirst().expiresAt())
                && inspected < CLEANUP_BATCH_SIZE) {
            SourceExpiry expiry = expiries.removeFirst();
            SourceState state = states.get(expiry.sourceHash());
            if (state != null
                    && state.active == 0
                    && state.windowStartedAt.plus(WINDOW).equals(expiry.expiresAt())) {
                states.remove(expiry.sourceHash(), state);
            }
            inspected++;
        }
        nextCleanupAt = now.plus(CLEANUP_INTERVAL);
    }

    int trackedSourceCount() {
        synchronized (states) {
            return states.size();
        }
    }

    private int secondsUntilWindowReset(Instant windowStartedAt, Instant now) {
        long elapsedMillis = Duration.between(windowStartedAt, now).toMillis();
        long remainingMillis = Math.max(1, WINDOW.toMillis() - elapsedMillis);
        return Math.toIntExact(Math.max(1, (remainingMillis + 999) / 1_000));
    }

    private void release(String sourceHash) {
        synchronized (states) {
            SourceState state = states.get(sourceHash);
            if (state != null && state.active > 0) {
                state.active--;
                if (state.active == 0
                        && !clock.instant().isBefore(state.windowStartedAt.plus(WINDOW))) {
                    states.remove(sourceHash, state);
                }
            }
        }
    }

    private static final class SourceState {

        private Instant windowStartedAt;
        private int requests;
        private int active;

        private SourceState(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private boolean resetWindowIfExpired(Instant now) {
            if (!now.isBefore(windowStartedAt.plus(WINDOW))) {
                windowStartedAt = now;
                requests = 0;
                return true;
            }
            return false;
        }
    }

    private static final class SourceExpiry {

        private final String sourceHash;
        private final Instant expiresAt;

        private SourceExpiry(String sourceHash, Instant expiresAt) {
            this.sourceHash = sourceHash;
            this.expiresAt = expiresAt;
        }

        private String sourceHash() {
            return sourceHash;
        }

        private Instant expiresAt() {
            return expiresAt;
        }
    }
}
