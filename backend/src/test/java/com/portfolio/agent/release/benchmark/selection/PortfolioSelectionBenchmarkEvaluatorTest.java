package com.portfolio.agent.release.benchmark.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioSelectionBenchmarkEvaluatorTest {
    @Test
    void scoresBestAcceptableSetAndAbsoluteSafetyCounts() {
        PortfolioSelectionBenchmarkCase benchmarkCase = new PortfolioSelectionBenchmarkCase(
                "CASE-1", BenchmarkSplit.HOLDOUT,
                new SelectionTarget("JAVA_BACKEND", "RECRUITER", Set.of("ASYNC_TASK", "SAFETY"), 2),
                List.of(Set.of("subject-a", "subject-b"), Set.of("subject-a", "subject-c")),
                Set.of("ASYNC_TASK", "SAFETY"));
        PortfolioSelectionObservation observation = new PortfolioSelectionObservation(
                BenchmarkRoute.R4, "CASE-1", "2026-07-29.1",
                List.of("subject-a", "subject-c", "subject-z"), List.of("subject-a", "subject-c"), 25,
                "HYBRID", "EXHAUSTIVE", ObservationState.AVAILABLE, null, true,
                List.of(
                        new SelectedSubjectObservation(
                                "subject-a", "2026-07-29.1", Set.of("ASYNC_TASK"), true, true),
                        new SelectedSubjectObservation(
                                "subject-c", "2026-07-29.1", Set.of("SAFETY"), false, false)));

        RouteBenchmarkMetrics metrics = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate("2026-07-29.1", List.of(benchmarkCase), List.of(observation), null)
                .getRoutes().get(BenchmarkRoute.R4);

        assertThat(metrics.getRecallAt12()).isEqualTo(1.0);
        assertThat(metrics.getHitAt1()).isEqualTo(1.0);
        assertThat(metrics.getHitAt5()).isEqualTo(1.0);
        assertThat(metrics.getMeanReciprocalRank()).isEqualTo(1.0);
        assertThat(metrics.getNormalizedDiscountedCumulativeGain()).isEqualTo(1.0);
        assertThat(metrics.getCapabilityCoverage()).isEqualTo(1.0);
        assertThat(metrics.getUnsupportedRecommendationCount()).isEqualTo(1);
        assertThat(metrics.getInvalidEvidenceCount()).isEqualTo(1);
        assertThat(metrics.getFalseSufficientCount()).isEqualTo(1);
    }

    @Test
    void preservesUnavailableRoutesInsteadOfConvertingThemToZero() {
        PortfolioSelectionBenchmarkCase benchmarkCase = benchmark("CASE-1", BenchmarkSplit.CALIBRATION);
        PortfolioSelectionBenchmarkReport report = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate("2026-07-29.1", List.of(benchmarkCase), List.of(), null);
        assertThat(report.getRoutes()).hasSize(5);
        assertThat(report.getRoutes().values())
                .allMatch(metrics -> metrics.getAvailability() == RouteAvailability.UNAVAILABLE);
    }

    @Test
    void calculatesStableLatencyPercentilesAndRedundancy() {
        PortfolioSelectionBenchmarkCase first = benchmark("A", BenchmarkSplit.REGRESSION);
        PortfolioSelectionBenchmarkCase second = benchmark("B", BenchmarkSplit.REGRESSION);
        RouteBenchmarkMetrics metrics = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate("2026-07-29.1", List.of(second, first),
                        List.of(observation("B", 30), observation("A", 10)), null)
                .getRoutes().get(BenchmarkRoute.R3);
        assertThat(metrics.getP50LatencyMilliseconds()).isEqualTo(10);
        assertThat(metrics.getP95LatencyMilliseconds()).isEqualTo(30);
        assertThat(metrics.getRedundancy()).isEqualTo(0.5);
    }

    @Test
    void countsCrossReleaseMixWithoutAveragingItAway() {
        PortfolioSelectionBenchmarkCase benchmarkCase = benchmark("A", BenchmarkSplit.HOLDOUT);
        PortfolioSelectionObservation observation = new PortfolioSelectionObservation(
                BenchmarkRoute.R2, "A", "2026-07-29.1", List.of("subject-a"),
                List.of("subject-a"), 8, "VECTOR", "TOP_K", ObservationState.AVAILABLE,
                null, true, List.of(new SelectedSubjectObservation(
                        "subject-a", "older-release", Set.of("DELIVERY"), true, true)));

        RouteBenchmarkMetrics metrics = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate("2026-07-29.1", List.of(benchmarkCase), List.of(observation), null)
                .getRoutes().get(BenchmarkRoute.R2);

        assertThat(metrics.getCrossReleaseMixCount()).isEqualTo(1);
        assertThat(metrics.getFalseSufficientCount()).isEqualTo(1);
    }

    @Test
    void duplicateOrExcessSelectionIsFalseSufficientAndOverallDoesNotFillMissingRoutesWithZero() {
        PortfolioSelectionBenchmarkCase benchmarkCase = benchmark("A", BenchmarkSplit.REGRESSION);
        PortfolioSelectionObservation observation = new PortfolioSelectionObservation(
                BenchmarkRoute.R4, "A", "2026-07-29.1", List.of("subject-a"),
                List.of("subject-a", "subject-a"), 7, "HYBRID", "EXHAUSTIVE",
                ObservationState.AVAILABLE, null, true,
                List.of(
                        new SelectedSubjectObservation(
                                "subject-a", "2026-07-29.1", Set.of("DELIVERY"), true, true),
                        new SelectedSubjectObservation(
                                "subject-a", "2026-07-29.1", Set.of("DELIVERY"), true, true)));

        PortfolioSelectionBenchmarkReport report = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate("2026-07-29.1", List.of(benchmarkCase), List.of(observation), null);

        assertThat(report.getRoutes().get(BenchmarkRoute.R4).getFalseSufficientCount()).isEqualTo(1);
        assertThat(report.getOverall().getFalseSufficientCount()).isEqualTo(1);
        assertThat(report.getOverall().getAvailability()).isEqualTo(RouteAvailability.PARTIAL);
        assertThat(report.getOverall().getUnavailableCaseCount()).isEqualTo(4);
    }

    @Test
    void ndcgAt12IsNonPerfectForLateRelevantResultAndStableAcrossAcceptableSetOrder() {
        SelectionTarget target = new SelectionTarget(null, "HR", Set.of("DELIVERY"), 2);
        Set<String> first = Set.of("subject-a", "subject-b");
        Set<String> second = Set.of("subject-a", "subject-c");
        PortfolioSelectionObservation observation = new PortfolioSelectionObservation(
                BenchmarkRoute.R1, "A", "2026-07-29.1",
                List.of("irrelevant", "subject-a", "subject-b"), List.of(), 3,
                "FTS", "TOP_K", ObservationState.AVAILABLE, null, false, List.of());
        PortfolioSelectionBenchmarkEvaluator evaluator = new PortfolioSelectionBenchmarkEvaluator();
        double forward = evaluator.evaluate(
                "2026-07-29.1",
                List.of(new PortfolioSelectionBenchmarkCase(
                        "A", BenchmarkSplit.HOLDOUT, target, List.of(first, second), Set.of("DELIVERY"))),
                List.of(observation), null).getRoutes().get(BenchmarkRoute.R1)
                .getNormalizedDiscountedCumulativeGain();
        double reversed = evaluator.evaluate(
                "2026-07-29.1",
                List.of(new PortfolioSelectionBenchmarkCase(
                        "A", BenchmarkSplit.HOLDOUT, target, List.of(second, first), Set.of("DELIVERY"))),
                List.of(observation), null).getRoutes().get(BenchmarkRoute.R1)
                .getNormalizedDiscountedCumulativeGain();

        assertThat(forward).isBetween(0.0, 0.999999);
        assertThat(reversed).isEqualTo(forward);
    }

    private PortfolioSelectionBenchmarkCase benchmark(String id, BenchmarkSplit split) {
        return new PortfolioSelectionBenchmarkCase(
                id, split, new SelectionTarget(null, "RECRUITER", Set.of("DELIVERY"), 2),
                List.of(Set.of("subject-a")), Set.of("DELIVERY"));
    }

    private PortfolioSelectionObservation observation(String id, long elapsed) {
        return new PortfolioSelectionObservation(
                BenchmarkRoute.R3, id, "2026-07-29.1", List.of("subject-a"),
                List.of("subject-a", "subject-b"), elapsed, "HYBRID", "TOP_K",
                ObservationState.AVAILABLE, null, false,
                List.of(
                        new SelectedSubjectObservation(
                                "subject-a", "2026-07-29.1", Set.of("DELIVERY"), true, true),
                        new SelectedSubjectObservation(
                                "subject-b", "2026-07-29.1", Set.of("DELIVERY"), true, true)));
    }
}
