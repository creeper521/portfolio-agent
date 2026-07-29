package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.DurationBucket;

public final class DurationBuckets {

    private DurationBuckets() {
    }

    public static DurationBucket fromElapsedMillis(long elapsedMillis) {
        if (elapsedMillis < 100) {
            return DurationBucket.LT_100_MS;
        }
        if (elapsedMillis < 500) {
            return DurationBucket.FROM_100_TO_499_MS;
        }
        if (elapsedMillis < 2000) {
            return DurationBucket.FROM_500_TO_1999_MS;
        }
        return DurationBucket.GE_2000_MS;
    }
}
