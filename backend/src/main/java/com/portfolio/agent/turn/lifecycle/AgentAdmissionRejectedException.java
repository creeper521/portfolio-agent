package com.portfolio.agent.turn.lifecycle;

import java.util.Objects;

/**
 * Agent Turn 准入失败。
 *
 * <p>内部原因仅用于诊断，公开 API 一律投影为 RATE_LIMITED，避免暴露容量细节。</p>
 */
public final class AgentAdmissionRejectedException extends RuntimeException {
    private final RejectionReason reason;
    private final int retryAfterSeconds;

    public AgentAdmissionRejectedException(
            RejectionReason reason,
            int retryAfterSeconds) {
        super("Agent Turn admission rejected");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RejectionReason getReason() {
        return reason;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum RejectionReason {
        SOURCE_RPM_LIMIT,
        SOURCE_CONCURRENCY_LIMIT,
        GLOBAL_ACTIVE_TURN_LIMIT
    }
}
