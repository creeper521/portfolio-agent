package com.portfolio.agent.common.observability;

/**
 * 耗时分桶枚举：把精确耗时粗化为四个区间标识，供 diagnostic 事件以
 * duration.bucket 字段发布性能信息。
 *
 * <p>使用分桶而非精确毫秒值，是为了在暴露性能趋势的同时不泄漏精确时序细节。</p>
 */
public enum DurationBucket {
    LT_100_MS,
    FROM_100_TO_499_MS,
    FROM_500_TO_1999_MS,
    GE_2000_MS
}
