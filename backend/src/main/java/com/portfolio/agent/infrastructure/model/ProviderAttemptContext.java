package com.portfolio.agent.infrastructure.model;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 单次 Provider HTTP attempt 的内部身份与低基数计量上下文。
 *
 * <p>UUID 只用于进程内区分 attempt，不得进入日志、诊断字段或公开投影。
 * {@link #toString()} 因此只输出 index/count 与重复计费风险。</p>
 */
public final class ProviderAttemptContext {
    private final UUID attemptId;
    private final int attemptIndex;
    private final int attemptCount;
    private final boolean duplicateBillingRisk;
    private final Duration attemptTimeoutCap;

    public ProviderAttemptContext(
            UUID attemptId,
            int attemptIndex,
            int attemptCount,
            boolean duplicateBillingRisk) {
        this(attemptId, attemptIndex, attemptCount,
                duplicateBillingRisk, null);
    }

    public ProviderAttemptContext(
            UUID attemptId,
            int attemptIndex,
            int attemptCount,
            boolean duplicateBillingRisk,
            Duration attemptTimeoutCap) {
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId");
        if (attemptCount < 1 || attemptCount > 2
                || attemptIndex < 1 || attemptIndex > attemptCount) {
            throw new IllegalArgumentException("attempt shape is invalid");
        }
        if (duplicateBillingRisk != (attemptIndex > 1)) {
            throw new IllegalArgumentException(
                    "duplicate billing risk must match a repeated attempt");
        }
        this.attemptIndex = attemptIndex;
        this.attemptCount = attemptCount;
        this.duplicateBillingRisk = duplicateBillingRisk;
        if (attemptTimeoutCap != null
                && (attemptTimeoutCap.isZero()
                || attemptTimeoutCap.isNegative())) {
            throw new IllegalArgumentException(
                    "attempt timeout cap must be positive");
        }
        this.attemptTimeoutCap = attemptTimeoutCap;
    }

    public static ProviderAttemptContext single(UUID attemptId) {
        return new ProviderAttemptContext(attemptId, 1, 1, false);
    }

    public UUID attemptId() { return attemptId; }
    public int attemptIndex() { return attemptIndex; }
    public int attemptCount() { return attemptCount; }
    public boolean duplicateBillingRisk() { return duplicateBillingRisk; }
    public Optional<Duration> attemptTimeoutCap() {
        return Optional.ofNullable(attemptTimeoutCap);
    }

    @Override
    public String toString() {
        return "ProviderAttemptContext[attemptIndex=" + attemptIndex
                + ", attemptCount=" + attemptCount
                + ", duplicateBillingRisk=" + duplicateBillingRisk + "]";
    }
}
