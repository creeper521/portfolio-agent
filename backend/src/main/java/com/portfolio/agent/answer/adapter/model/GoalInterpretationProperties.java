package com.portfolio.agent.answer.adapter.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Bounded transport settings for the single Goal Interpretation operation. */
@ConfigurationProperties(prefix = "portfolio.goal-interpretation")
public final class GoalInterpretationProperties {

    private Duration timeout = Duration.ofMillis(2500);
    private int maxOutputTokens = 1600;

    public void validate() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("goal interpretation timeout is invalid");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > 4000) {
            throw new IllegalArgumentException("goal interpretation output budget is invalid");
        }
    }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
}
