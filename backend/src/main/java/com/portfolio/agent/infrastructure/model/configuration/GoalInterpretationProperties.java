package com.portfolio.agent.infrastructure.model.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Output contract settings for the single Goal Interpretation operation. */
@ConfigurationProperties(prefix = "portfolio.goal-interpretation")
public final class GoalInterpretationProperties {

    private int maxOutputTokens = 1600;

    public void validate() {
        if (maxOutputTokens < 1 || maxOutputTokens > 4000) {
            throw new IllegalArgumentException("goal interpretation output budget is invalid");
        }
    }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
}
