package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.exception.AnswerAdmissionRejectedException;
import com.portfolio.agent.answer.exception.AnswerErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AnswerAdmissionGate {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_TRACKED_SOURCES = 10_000;

    private final Clock clock;
    private final int requestsPerMinute;
    private final int maxConcurrent;
    private final int maxTrackedSources;
    private final Map<String, SourceState> states = new HashMap<>();

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
        var now = clock.instant();

        synchronized (states) {
            states.entrySet().removeIf(entry ->
                    entry.getValue().active == 0
                            && !now.isBefore(entry.getValue().windowStartedAt.plus(WINDOW)));
            if (!states.containsKey(sourceHash) && states.size() >= maxTrackedSources) {
                throw new AnswerAdmissionRejectedException(
                        AnswerErrorCode.ANSWER_RATE_LIMITED, 60);
            }
            var state = states.computeIfAbsent(sourceHash, ignored -> new SourceState(now));
            state.resetWindowIfExpired(now);

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
            var state = states.get(sourceHash);
            if (state != null && state.active > 0) {
                state.active--;
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

        private void resetWindowIfExpired(Instant now) {
            if (!now.isBefore(windowStartedAt.plus(WINDOW))) {
                windowStartedAt = now;
                requests = 0;
            }
        }
    }
}
