package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalBenchmarkEvaluatorTest {

    @Test
    void calculatesSeparateSplitCategorySubjectAndDecisionAggregates() {
        RetrievalBenchmarkEvaluator evaluator = new RetrievalBenchmarkEvaluator();
        List<RetrievalRouteEvaluation> evaluations = List.of(
                evaluation(
                        RetrievalBenchmarkRoute.HYBRID,
                        "holdout-positive",
                        RetrievalBenchmarkSplit.HOLDOUT,
                        RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                        "project-a",
                        RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT,
                        1
                ),
                evaluation(
                        RetrievalBenchmarkRoute.HYBRID,
                        "calibration-negative",
                        RetrievalBenchmarkSplit.CALIBRATION,
                        RetrievalBenchmarkCategory.OUT_OF_SCOPE,
                        "project-b",
                        RetrievalDecisionType.INSUFFICIENT,
                        RetrievalDecisionType.AMBIGUOUS,
                        null
                )
        );

        assertThat(evaluator.evaluateBySplitAndRoute(evaluations))
                .extracting(
                        RetrievalBenchmarkGroupMetrics::getSplit,
                        RetrievalBenchmarkGroupMetrics::getRoute,
                        group -> group.getMetrics().getPositiveCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.CALIBRATION,
                                RetrievalBenchmarkRoute.HYBRID,
                                0),
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.HOLDOUT,
                                RetrievalBenchmarkRoute.HYBRID,
                                1)
                );
        assertThat(evaluator.evaluateBySplitCategoryAndRoute(evaluations))
                .extracting(
                        RetrievalBenchmarkGroupMetrics::getSplit,
                        RetrievalBenchmarkGroupMetrics::getCategory,
                        RetrievalBenchmarkGroupMetrics::getRoute)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.CALIBRATION,
                                RetrievalBenchmarkCategory.OUT_OF_SCOPE,
                                RetrievalBenchmarkRoute.HYBRID),
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.HOLDOUT,
                                RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                                RetrievalBenchmarkRoute.HYBRID)
                );
        assertThat(evaluator.evaluateBySplitSubjectAndRoute(evaluations))
                .extracting(
                        RetrievalBenchmarkGroupMetrics::getSplit,
                        RetrievalBenchmarkGroupMetrics::getSubjectType,
                        RetrievalBenchmarkGroupMetrics::getSubjectSlug,
                        RetrievalBenchmarkGroupMetrics::getRoute)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.CALIBRATION,
                                com.portfolio.agent.portfolio.domain.ClaimSubjectType.PROJECT,
                                "project-b",
                                RetrievalBenchmarkRoute.HYBRID),
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.HOLDOUT,
                                com.portfolio.agent.portfolio.domain.ClaimSubjectType.PROJECT,
                                "project-a",
                                RetrievalBenchmarkRoute.HYBRID)
                );
        assertThat(evaluator.countBySplitRouteAndDecision(evaluations))
                .extracting(
                        RetrievalDecisionCount::getSplit,
                        RetrievalDecisionCount::getRoute,
                        RetrievalDecisionCount::getActualDecision,
                        RetrievalDecisionCount::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.CALIBRATION,
                                RetrievalBenchmarkRoute.HYBRID,
                                RetrievalDecisionType.AMBIGUOUS,
                                1),
                        org.assertj.core.groups.Tuple.tuple(
                                RetrievalBenchmarkSplit.HOLDOUT,
                                RetrievalBenchmarkRoute.HYBRID,
                                RetrievalDecisionType.SUFFICIENT,
                                1)
                );
    }

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

    private RetrievalRouteEvaluation evaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            String subjectSlug,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank
    ) {
        return new RetrievalRouteEvaluation(
                route,
                caseId,
                split,
                category,
                com.portfolio.agent.portfolio.domain.ClaimSubjectType.PROJECT,
                subjectSlug,
                expectedDecision,
                actualDecision,
                expectedRank,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
