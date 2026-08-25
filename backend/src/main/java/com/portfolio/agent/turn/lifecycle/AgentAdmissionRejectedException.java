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

    /** 触发拒绝的具体资源维度；仅用于内部诊断与精确的 Retry-After 计算。 */
    public enum RejectionReason {
        /** 固定窗口内该来源的 RPM 已耗尽。 */
        SOURCE_RPM_LIMIT,
        /** 该来源并发中的请求数已达上限。 */
        SOURCE_CONCURRENCY_LIMIT,
        /** 单实例全局 Active Turn 许可已耗尽。 */
        GLOBAL_ACTIVE_TURN_LIMIT
    }
}
