package com.portfolio.agent.common.observability;

public enum DurationBucket {
    LT_100_MS,
    FROM_100_TO_499_MS,
    FROM_500_TO_1999_MS,
    GE_2000_MS
}
