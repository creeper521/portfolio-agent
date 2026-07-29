package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.DurationBucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationBucketsTest {

    @Test
    void mapsElapsedMillisToStableBuckets() {
        assertThat(DurationBuckets.fromElapsedMillis(0)).isEqualTo(DurationBucket.LT_100_MS);
        assertThat(DurationBuckets.fromElapsedMillis(99)).isEqualTo(DurationBucket.LT_100_MS);
        assertThat(DurationBuckets.fromElapsedMillis(100))
                .isEqualTo(DurationBucket.FROM_100_TO_499_MS);
        assertThat(DurationBuckets.fromElapsedMillis(499))
                .isEqualTo(DurationBucket.FROM_100_TO_499_MS);
        assertThat(DurationBuckets.fromElapsedMillis(500))
                .isEqualTo(DurationBucket.FROM_500_TO_1999_MS);
        assertThat(DurationBuckets.fromElapsedMillis(1999))
                .isEqualTo(DurationBucket.FROM_500_TO_1999_MS);
        assertThat(DurationBuckets.fromElapsedMillis(2000)).isEqualTo(DurationBucket.GE_2000_MS);
    }
}
