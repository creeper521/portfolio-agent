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

    /**
     * 为一次来源请求获取准入租约：固定 1 分钟窗口 RPM + 来源并发上限。
     *
     * <p>成功时占用一个窗口计数与一个并发位，返回必须关闭的
     * {@link AgentRequestAdmission}；来源表达到容量上限时先做一次强制清理，
     * 仍无空间则按 RPM 语义拒绝（fail-closed，不驱逐他人）。</p>
     *
     * @param sourceHash 进程级匿名来源哈希，本类不接触原始地址
     * @throws AgentAdmissionRejectedException 窗口配额、来源并发或来源表容量耗尽
     */
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

    /** 当前跟踪的来源数（测试观察口）。 */
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

    /**
     * 到期队列的批量清理：默认按 1 秒节流，force 时立即执行。
     * 每轮最多检查 {@value CLEANUP_BATCH_SIZE} 条；只有窗口已到期、无并发占用且
     * 到期记录与当前窗口世代一致的来源才会被移除，避免误删已续期的来源。
     */
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

    /** 计算距窗口重置的秒数（向上取整且至少 1 秒），用于 Retry-After。 */
    private int secondsUntilWindowReset(Instant windowStartedAt, Instant now) {
        long elapsedMillis = Duration.between(windowStartedAt, now).toMillis();
        long remainingMillis = Math.max(1, WINDOW.toMillis() - elapsedMillis);
        return Math.toIntExact(Math.max(1, (remainingMillis + 999) / 1_000));
    }

    /** 释放来源并发位；并发归零且窗口已过期时顺带移除来源状态，防止占用追踪名额。 */
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

    /** 单个来源的固定窗口计数状态：窗口起点、窗口内请求数与当前并发数。 */
    private static final class SourceState {
        private Instant windowStartedAt;
        private int requests;
        private int active;

        private SourceState(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        /** 窗口过期时重置窗口起点与计数；返回是否发生了重置（用于登记新到期记录）。 */
        private boolean resetWindowIfExpired(Instant now) {
            if (now.isBefore(windowStartedAt.plus(WINDOW))) {
                return false;
            }
            windowStartedAt = now;
            requests = 0;
            return true;
        }
    }

    /** 到期队列条目：来源哈希与其登记时的窗口到期时间。 */
    private static final class SourceExpiry {
        private final String sourceHash;
        private final Instant expiresAt;

        private SourceExpiry(String sourceHash, Instant expiresAt) {
            this.sourceHash = sourceHash;
            this.expiresAt = expiresAt;
        }
    }
}
