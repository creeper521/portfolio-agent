package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RetrievalBenchmarkEvaluator {

    public Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> evaluate(
            List<RetrievalRouteEvaluation> evaluations
    ) {
        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("evaluations must not be empty");
        }

        Map<RetrievalBenchmarkRoute, MetricAccumulator> accumulators = new EnumMap<>(RetrievalBenchmarkRoute.class);
        for (RetrievalBenchmarkRoute route : RetrievalBenchmarkRoute.values()) {
            accumulators.put(route, new MetricAccumulator());
        }
        for (RetrievalRouteEvaluation evaluation : evaluations) {
            accumulators.get(evaluation.getRoute()).add(evaluation);
        }

        Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metrics = new EnumMap<>(RetrievalBenchmarkRoute.class);
        for (RetrievalBenchmarkRoute route : RetrievalBenchmarkRoute.values()) {
            metrics.put(route, accumulators.get(route).toMetrics());
        }
        return metrics;
    }

    private static final class MetricAccumulator {

        private int positiveCount;
        private int hitAt1Count;
        private int hitAt5Count;
        private double reciprocalRankSum;
        private int falseSufficientCount;

        private void add(RetrievalRouteEvaluation evaluation) {
            if (evaluation.getExpectedDecision() == RetrievalDecisionType.SUFFICIENT) {
                positiveCount++;
                Integer expectedRank = evaluation.getExpectedRank();
                if (expectedRank != null && expectedRank <= 5) {
                    hitAt5Count++;
                    reciprocalRankSum += 1.0 / expectedRank;
                    if (expectedRank == 1) {
                        hitAt1Count++;
                    }
                }
            }
            if (evaluation.getExpectedDecision() != RetrievalDecisionType.SUFFICIENT
                    && evaluation.getActualDecision() == RetrievalDecisionType.SUFFICIENT) {
                falseSufficientCount++;
            }
        }

        private RetrievalBenchmarkMetrics toMetrics() {
            double hitAt1 = positiveCount == 0 ? 0.0 : hitAt1Count / (double) positiveCount;
            double hitAt5 = positiveCount == 0 ? 0.0 : hitAt5Count / (double) positiveCount;
            double mrrAt5 = positiveCount == 0 ? 0.0 : reciprocalRankSum / positiveCount;
            return new RetrievalBenchmarkMetrics(
                    positiveCount,
                    hitAt1,
                    hitAt5,
                    mrrAt5,
                    falseSufficientCount
            );
        }
    }
}
