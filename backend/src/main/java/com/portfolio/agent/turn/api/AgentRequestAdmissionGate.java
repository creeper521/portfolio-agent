package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.lifecycle.AgentAdmissionRejectedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 按匿名来源执行固定窗口 RPM 与并发准入。
 *
 * <p>来源只能使用进程级 HMAC；本类不得接触或保存原始地址。</p>
 */
public final class AgentRequestAdmissionGate {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(1);
    private static final int CLEANUP_BATCH_SIZE = 256;

    private final Clock clock;
    private final int requestsPerMinute;
    private final int maxConcurrent;
    private final int maxTrackedSources;
    private final Map<String, SourceState> states = new HashMap<>();
    private final Deque<SourceExpiry> expiries = new ArrayDeque<>();
    private Instant nextCleanupAt = Instant.MIN;

    public AgentRequestAdmissionGate(
            Clock clock,
            int requestsPerMinute,
            int maxConcurrent,
            int maxTrackedSources) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.requestsPerMinute = positive(requestsPerMinute, "requestsPerMinute");
        this.maxConcurrent = positive(maxConcurrent, "maxConcurrent");
        this.maxTrackedSources = positive(maxTrackedSources, "maxTrackedSources");
    }

    public AgentRequestAdmission acquire(String sourceHash, UUID requestId) {
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (sourceHash.isBlank()) {
            throw new IllegalArgumentException("sourceHash must not be blank");
        }
        Instant now = clock.instant();

        synchronized (states) {
            cleanupExpired(now, false);
            if (!states.containsKey(sourceHash) && states.size() >= maxTrackedSources) {
                cleanupExpired(now, true);
            }
            if (!states.containsKey(sourceHash) && states.size() >= maxTrackedSources) {
                throw rejected(
                        AgentAdmissionRejectedException.RejectionReason.SOURCE_RPM_LIMIT,
                        Math.toIntExact(WINDOW.toSeconds()));
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
                throw rejected(
                        AgentAdmissionRejectedException.RejectionReason.SOURCE_RPM_LIMIT,
                        secondsUntilWindowReset(state.windowStartedAt, now));
            }
            if (state.active >= maxConcurrent) {
                throw rejected(
                        AgentAdmissionRejectedException.RejectionReason.SOURCE_CONCURRENCY_LIMIT,
                        1);
            }

            state.requests++;
            state.active++;
        }

        return new AgentRequestAdmission(() -> release(sourceHash));
    }

    int trackedSourceCount() {
        synchronized (states) {
            return states.size();
        }
    }

    private AgentAdmissionRejectedException rejected(
            AgentAdmissionRejectedException.RejectionReason reason,
            int retryAfterSeconds) {
        return new AgentAdmissionRejectedException(reason, retryAfterSeconds);
    }

    private void cleanupExpired(Instant now, boolean force) {
        if (!force && now.isBefore(nextCleanupAt)) {
            return;
        }
        int inspected = 0;
        while (!expiries.isEmpty()
                && !now.isBefore(expiries.peekFirst().expiresAt)
                && inspected < CLEANUP_BATCH_SIZE) {
            SourceExpiry expiry = expiries.removeFirst();
            SourceState state = states.get(expiry.sourceHash);
            if (state != null
                    && state.active == 0
                    && state.windowStartedAt.plus(WINDOW).equals(expiry.expiresAt)) {
                states.remove(expiry.sourceHash, state);
            }
            inspected++;
        }
        nextCleanupAt = now.plus(CLEANUP_INTERVAL);
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

    private int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static final class SourceState {
        private Instant windowStartedAt;
        private int requests;
        private int active;

        private SourceState(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private boolean resetWindowIfExpired(Instant now) {
            if (now.isBefore(windowStartedAt.plus(WINDOW))) {
                return false;
            }
            windowStartedAt = now;
            requests = 0;
            return true;
        }
    }

    private static final class SourceExpiry {
        private final String sourceHash;
        private final Instant expiresAt;

        private SourceExpiry(String sourceHash, Instant expiresAt) {
            this.sourceHash = sourceHash;
            this.expiresAt = expiresAt;
        }
    }
}
