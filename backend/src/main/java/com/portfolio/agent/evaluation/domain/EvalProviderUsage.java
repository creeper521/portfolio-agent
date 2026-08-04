package com.portfolio.agent.evaluation.domain;

import java.util.Objects;

/**
 * Numeric Provider usage when the Provider exposed it, or an explicit unavailable state.
 */
public final class EvalProviderUsage {

    private final EvalProviderUsageAvailability availability;
    private final Long inputTokens;
    private final Long outputTokens;
    private final Long totalTokens;

    private EvalProviderUsage(EvalProviderUsageAvailability availability, Long inputTokens,
                              Long outputTokens, Long totalTokens) {
        this.availability = Objects.requireNonNull(availability, "availability");
        if (availability == EvalProviderUsageAvailability.UNAVAILABLE) {
            if (inputTokens != null || outputTokens != null || totalTokens != null) {
                throw new IllegalArgumentException("unavailable usage must not contain token values");
            }
        } else {
            this.inputTokens = nonNegative(inputTokens, "inputTokens");
            this.outputTokens = nonNegative(outputTokens, "outputTokens");
            this.totalTokens = nonNegative(totalTokens, "totalTokens");
            return;
        }
        this.inputTokens = null;
        this.outputTokens = null;
        this.totalTokens = null;
    }

    public static EvalProviderUsage unavailable() {
        return new EvalProviderUsage(EvalProviderUsageAvailability.UNAVAILABLE, null, null, null);
    }

    public static EvalProviderUsage available(long inputTokens, long outputTokens, long totalTokens) {
        return new EvalProviderUsage(EvalProviderUsageAvailability.AVAILABLE, inputTokens,
                outputTokens, totalTokens);
    }

    private static Long nonNegative(Long value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative when usage is available");
        }
        return value;
    }

    public EvalProviderUsageAvailability getAvailability() { return availability; }
    public boolean isAvailable() { return availability == EvalProviderUsageAvailability.AVAILABLE; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public Long getTotalTokens() { return totalTokens; }
}
