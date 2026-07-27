package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;

import java.util.Objects;

public final class RetrievalDecisionCount {

    private final RetrievalBenchmarkSplit split;
    private final RetrievalBenchmarkRoute route;
    private final RetrievalDecisionType actualDecision;
    private final int count;

    public RetrievalDecisionCount(
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkRoute route,
            RetrievalDecisionType actualDecision,
            int count
    ) {
        this.split = Objects.requireNonNull(split, "split");
        this.route = Objects.requireNonNull(route, "route");
        this.actualDecision = Objects.requireNonNull(
                actualDecision, "actualDecision");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        this.count = count;
    }

    public RetrievalBenchmarkSplit getSplit() { return split; }
    public RetrievalBenchmarkRoute getRoute() { return route; }
    public RetrievalDecisionType getActualDecision() { return actualDecision; }
    public int getCount() { return count; }
}
