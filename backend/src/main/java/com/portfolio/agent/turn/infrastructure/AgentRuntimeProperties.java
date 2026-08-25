package com.portfolio.agent.turn.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Agent 运行时限流与预算配置（portfolio.agent-runtime 前缀）。
 *
 * <p>限流维度：来源 RPM/并发、全局 Active Turn 上限、来源追踪容量；时间预算：
 * Turn 超时、结算预留、认领租期与数据库操作超时。所有数值 setter 都做正数校验，
 * 时间预算关系在 {@link #validateBudgetRelation} 与初始化时 fail-closed 校验。</p>
 */
@ConfigurationProperties(prefix = "portfolio.agent-runtime")
public final class AgentRuntimeProperties implements InitializingBean {
    private int requestsPerMinute = 10;
    private int maxConcurrentPerSource = 2;
    private int maxActiveTurns = 8;
    private int maxTrackedSources = 10_000;
    private Duration leaseDuration = Duration.ofSeconds(35);
    private Duration turnTimeout = Duration.ofSeconds(20);
    private Duration settlementReserve = Duration.ofSeconds(2);
    private Duration databaseOperationTimeout = Duration.ofSeconds(3);
    private boolean trustProxy;
    private Set<String> trustedProxies = new LinkedHashSet<>();

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int value) {
        requestsPerMinute = positive(value, "requestsPerMinute");
    }

    public int getMaxConcurrentPerSource() {
        return maxConcurrentPerSource;
    }

    public void setMaxConcurrentPerSource(int value) {
        maxConcurrentPerSource = positive(value, "maxConcurrentPerSource");
    }

    public int getMaxActiveTurns() {
        return maxActiveTurns;
    }

    public void setMaxActiveTurns(int value) {
        maxActiveTurns = positive(value, "maxActiveTurns");
    }

    public int getMaxTrackedSources() {
        return maxTrackedSources;
    }

    public void setMaxTrackedSources(int value) {
        maxTrackedSources = positive(value, "maxTrackedSources");
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration value) {
        leaseDuration = positive(value, "leaseDuration");
    }

    public Duration getTurnTimeout() {
        return turnTimeout;
    }

    public void setTurnTimeout(Duration value) {
        turnTimeout = positive(value, "turnTimeout");
    }

    public Duration getSettlementReserve() {
        return settlementReserve;
    }

    public void setSettlementReserve(Duration value) {
        settlementReserve = positive(value, "settlementReserve");
    }

    public Duration getDatabaseOperationTimeout() {
        return databaseOperationTimeout;
    }

    public void setDatabaseOperationTimeout(Duration value) {
        databaseOperationTimeout = positive(value, "databaseOperationTimeout");
    }

    /**
     * 校验时间预算关系（fail-closed）：结算预留短于 Turn 超时；认领租期严格大于
     * 超时+预留+数据库恢复预算；数据库操作超时短于执行窗口。违反任何一条都让
     * 应用启动失败，而不是带着可能丢结算的配置运行。
     */
    public void validateBudgetRelation() {
        if (settlementReserve.compareTo(turnTimeout) >= 0) {
            throw new IllegalStateException("settlement reserve must be shorter than turn timeout");
        }
        Duration minimumLease = turnTimeout.plus(settlementReserve)
                .plus(databaseOperationTimeout);
        if (leaseDuration.compareTo(minimumLease) <= 0) {
            throw new IllegalStateException(
                    "claim lease must exceed turn, settlement and recovery budgets");
        }
        Duration executionWindow = turnTimeout.minus(settlementReserve);
        if (databaseOperationTimeout.compareTo(executionWindow) >= 0) {
            throw new IllegalStateException("operation timeout must be shorter than execution window");
        }
    }

    /** 初始化即校验预算关系（实现 InitializingBean 的 fail-closed 入口）。 */
    @Override
    public void afterPropertiesSet() {
        validateBudgetRelation();
    }

    public boolean isTrustProxy() {
        return trustProxy;
    }

    public void setTrustProxy(boolean value) {
        trustProxy = value;
    }

    public Set<String> getTrustedProxies() {
        return Set.copyOf(trustedProxies);
    }

    public void setTrustedProxies(Set<String> value) {
        trustedProxies = value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value);
    }

    private int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
