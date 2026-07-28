package com.portfolio.agent.answer.adapter.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "portfolio.answer-production")
public final class AnswerProductionProperties {
    private int requestsPerMinute = 10;
    private int maxConcurrent = 2;
    private Duration requestTimeout = Duration.ofSeconds(12);
    private Duration idempotencyTtl = Duration.ofMinutes(2);
    private boolean trustProxy;
    private Set<String> trustedProxies = new LinkedHashSet<>();

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int value) { requestsPerMinute = positive(value); }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int value) { maxConcurrent = positive(value); }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = positive(value); }
    public Duration getIdempotencyTtl() { return idempotencyTtl; }
    public void setIdempotencyTtl(Duration value) { idempotencyTtl = positive(value); }
    public boolean isTrustProxy() { return trustProxy; }
    public void setTrustProxy(boolean value) { trustProxy = value; }
    public Set<String> getTrustedProxies() { return Set.copyOf(trustedProxies); }
    public void setTrustedProxies(Set<String> value) {
        trustedProxies = value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value);
    }

    private int positive(int value) {
        if (value <= 0) throw new IllegalArgumentException("answer production limit must be positive");
        return value;
    }
    private Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("answer production duration must be positive");
        }
        return value;
    }
}
