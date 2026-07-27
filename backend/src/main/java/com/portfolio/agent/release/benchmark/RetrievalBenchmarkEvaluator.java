package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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

    public List<RetrievalBenchmarkGroupMetrics> evaluateBySplitAndRoute(
            List<RetrievalRouteEvaluation> evaluations
    ) {
        requireEvaluations(evaluations);
        List<RetrievalBenchmarkGroupMetrics> result = new ArrayList<>();
        for (RetrievalBenchmarkSplit split : RetrievalBenchmarkSplit.values()) {
            for (RetrievalBenchmarkRoute route
                    : RetrievalBenchmarkRoute.values()) {
                List<RetrievalRouteEvaluation> matching = matching(
                        evaluations, split, null, null, null, route);
                if (!matching.isEmpty()) {
                    result.add(new RetrievalBenchmarkGroupMetrics(
                            split,
                            null,
                            null,
                            null,
                            route,
                            evaluate(matching).get(route)
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    public List<RetrievalBenchmarkGroupMetrics>
            evaluateBySplitCategoryAndRoute(
                    List<RetrievalRouteEvaluation> evaluations
            ) {
        requireEvaluations(evaluations);
        List<RetrievalBenchmarkGroupMetrics> result = new ArrayList<>();
        for (RetrievalBenchmarkSplit split : RetrievalBenchmarkSplit.values()) {
            for (RetrievalBenchmarkCategory category
                    : RetrievalBenchmarkCategory.values()) {
                for (RetrievalBenchmarkRoute route
                        : RetrievalBenchmarkRoute.values()) {
                    List<RetrievalRouteEvaluation> matching = matching(
                            evaluations,
                            split,
                            category,
                            null,
                            null,
                            route
                    );
                    if (!matching.isEmpty()) {
                        result.add(new RetrievalBenchmarkGroupMetrics(
                                split,
                                category,
                                null,
                                null,
                                route,
                                evaluate(matching).get(route)
                        ));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    public List<RetrievalBenchmarkGroupMetrics>
            evaluateBySplitSubjectAndRoute(
                    List<RetrievalRouteEvaluation> evaluations
            ) {
        requireEvaluations(evaluations);
        Map<String, Subject> subjects = new LinkedHashMap<>();
        evaluations.stream()
                .filter(evaluation -> evaluation.getSubjectType() != null)
                .filter(evaluation -> evaluation.getSubjectSlug() != null)
                .sorted(Comparator
                        .comparing((RetrievalRouteEvaluation evaluation) ->
                                evaluation.getSubjectType().name())
                        .thenComparing(
                                RetrievalRouteEvaluation::getSubjectSlug))
                .forEach(evaluation -> subjects.putIfAbsent(
                        evaluation.getSubjectType().name()
                                + "\u0000" + evaluation.getSubjectSlug(),
                        new Subject(
                                evaluation.getSubjectType(),
                                evaluation.getSubjectSlug())));
        List<RetrievalBenchmarkGroupMetrics> result = new ArrayList<>();
        for (RetrievalBenchmarkSplit split : RetrievalBenchmarkSplit.values()) {
            for (Subject subject : subjects.values()) {
                for (RetrievalBenchmarkRoute route
                        : RetrievalBenchmarkRoute.values()) {
                    List<RetrievalRouteEvaluation> matching = matching(
                            evaluations,
                            split,
                            null,
                            subject.type,
                            subject.slug,
                            route
                    );
                    if (!matching.isEmpty()) {
                        result.add(new RetrievalBenchmarkGroupMetrics(
                                split,
                                null,
                                subject.type,
                                subject.slug,
                                route,
                                evaluate(matching).get(route)
                        ));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    public List<RetrievalDecisionCount> countBySplitRouteAndDecision(
            List<RetrievalRouteEvaluation> evaluations
    ) {
        requireEvaluations(evaluations);
        List<RetrievalDecisionCount> result = new ArrayList<>();
        for (RetrievalBenchmarkSplit split : RetrievalBenchmarkSplit.values()) {
            for (RetrievalBenchmarkRoute route
                    : RetrievalBenchmarkRoute.values()) {
                for (RetrievalDecisionType decision
                        : RetrievalDecisionType.values()) {
                    int count = 0;
                    for (RetrievalRouteEvaluation evaluation : evaluations) {
                        if (evaluation.getSplit() == split
                                && evaluation.getRoute() == route
                                && evaluation.getActualDecision() == decision) {
                            count++;
                        }
                    }
                    if (count > 0) {
                        result.add(new RetrievalDecisionCount(
                                split, route, decision, count));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private List<RetrievalRouteEvaluation> matching(
            List<RetrievalRouteEvaluation> evaluations,
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            ClaimSubjectType subjectType,
            String subjectSlug,
            RetrievalBenchmarkRoute route
    ) {
        List<RetrievalRouteEvaluation> result = new ArrayList<>();
        for (RetrievalRouteEvaluation evaluation : evaluations) {
            if (evaluation.getSplit() == split
                    && evaluation.getRoute() == route
                    && (category == null
                    || evaluation.getCategory() == category)
                    && (subjectType == null
                    || evaluation.getSubjectType() == subjectType)
                    && (subjectSlug == null
                    || subjectSlug.equals(evaluation.getSubjectSlug()))) {
                result.add(evaluation);
            }
        }
        return result;
    }

    private void requireEvaluations(List<RetrievalRouteEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalArgumentException(
                    "evaluations must not be empty");
        }
    }

    private static final class MetricAccumulator {

        private int positiveCount;
        private int hitAt1Count;
        private int hitAt5Count;
        private double reciprocalRankSum;
        private int positiveDecisionSuccessCount;
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
                if (evaluation.getActualDecision()
                        == RetrievalDecisionType.SUFFICIENT) {
                    positiveDecisionSuccessCount++;
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
            double positiveDecisionSuccessRate = positiveCount == 0
                    ? 0.0
                    : positiveDecisionSuccessCount / (double) positiveCount;
            return new RetrievalBenchmarkMetrics(
                    positiveCount,
                    hitAt1,
                    hitAt5,
                    mrrAt5,
                    positiveDecisionSuccessCount,
                    positiveDecisionSuccessRate,
                    falseSufficientCount
            );
        }
    }

    private static final class Subject {

        private final ClaimSubjectType type;
        private final String slug;

        private Subject(ClaimSubjectType type, String slug) {
            this.type = type;
            this.slug = slug;
        }
    }
}
