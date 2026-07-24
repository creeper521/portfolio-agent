package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalBenchmarkEvaluatorTest {

    @Test
    void calculatesMetricsAndReturnsRoutesInDeterministicOrder() {
        RetrievalBenchmarkEvaluator evaluator = new RetrievalBenchmarkEvaluator();

        Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metricsByRoute = evaluator.evaluate(List.of(
                evaluation(RetrievalBenchmarkRoute.KEYWORD, "negative-correct", RetrievalDecisionType.INSUFFICIENT,
                        RetrievalDecisionType.INSUFFICIENT, null),
                evaluation(RetrievalBenchmarkRoute.HYBRID, "positive-rank-5", RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT, 5),
                evaluation(RetrievalBenchmarkRoute.HYBRID, "negative-false-sufficient", RetrievalDecisionType.INSUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT, null),
                evaluation(RetrievalBenchmarkRoute.HYBRID, "positive-rank-1", RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT, 1),
                evaluation(RetrievalBenchmarkRoute.HYBRID, "positive-absent", RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.AMBIGUOUS, null),
                evaluation(RetrievalBenchmarkRoute.HYBRID, "positive-rank-2", RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT, 2)
        ));

        RetrievalBenchmarkMetrics metrics = metricsByRoute.get(RetrievalBenchmarkRoute.HYBRID);
        assertThat(metrics.getPositiveCount()).isEqualTo(4);
        assertThat(metrics.getHitAt1()).isEqualTo(0.25);
        assertThat(metrics.getHitAt5()).isEqualTo(0.75);
        assertThat(metrics.getMrrAt5()).isEqualTo((1.0 + 0.5 + 0.2) / 4.0);
        assertThat(metrics.getPositiveDecisionSuccessCount()).isEqualTo(3);
        assertThat(metrics.getPositiveDecisionSuccessRate()).isEqualTo(0.75);
        assertThat(metrics.getFalseSufficientCount()).isEqualTo(1);
        assertThat(metricsByRoute.keySet()).containsExactly(
                RetrievalBenchmarkRoute.KEYWORD,
                RetrievalBenchmarkRoute.VECTOR,
                RetrievalBenchmarkRoute.HYBRID
        );
    }

    @Test
    void rejectsAnEmptyEvaluationSet() {
        RetrievalBenchmarkEvaluator evaluator = new RetrievalBenchmarkEvaluator();

        assertThatThrownBy(() -> evaluator.evaluate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RetrievalRouteEvaluation evaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank
    ) {
        return new RetrievalRouteEvaluation(
                route,
                caseId,
                RetrievalBenchmarkSplit.HOLDOUT,
                RetrievalBenchmarkCategory.EXACT_TERM,
                expectedDecision,
                actualDecision,
                expectedRank,
                List.of("claim-" + caseId),
                List.of("chunk-" + caseId)
        );
    }
}
