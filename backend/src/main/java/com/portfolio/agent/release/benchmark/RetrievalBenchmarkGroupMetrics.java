package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

import java.util.Objects;

public final class RetrievalBenchmarkGroupMetrics {

    private final RetrievalBenchmarkSplit split;
    private final RetrievalBenchmarkCategory category;
    private final ClaimSubjectType subjectType;
    private final String subjectSlug;
    private final RetrievalBenchmarkRoute route;
    private final RetrievalBenchmarkMetrics metrics;

    public RetrievalBenchmarkGroupMetrics(
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            ClaimSubjectType subjectType,
            String subjectSlug,
            RetrievalBenchmarkRoute route,
            RetrievalBenchmarkMetrics metrics
    ) {
        this.split = Objects.requireNonNull(split, "split");
        this.category = category;
        this.subjectType = subjectType;
        this.subjectSlug = subjectSlug;
        this.route = Objects.requireNonNull(route, "route");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public RetrievalBenchmarkSplit getSplit() { return split; }
    public RetrievalBenchmarkCategory getCategory() { return category; }
    public ClaimSubjectType getSubjectType() { return subjectType; }
    public String getSubjectSlug() { return subjectSlug; }
    public RetrievalBenchmarkRoute getRoute() { return route; }
    public RetrievalBenchmarkMetrics getMetrics() { return metrics; }
}
