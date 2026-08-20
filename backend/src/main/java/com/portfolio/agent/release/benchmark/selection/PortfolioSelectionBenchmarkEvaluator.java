package com.portfolio.agent.release.benchmark.selection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic public-only evaluator.
 *
 * <p>Retrieval metrics use binary relevance and the acceptable set producing the
 * highest nDCG (then lexical set order for ties). Recall is evaluated at 12,
 * Hit@k is binary, MRR uses the first relevant rank, and nDCG uses log2 discount.
 * Capability coverage is the required-capability union covered by selected
 * subjects. Redundancy is duplicate capability occurrences divided by all
 * selected capability occurrences. Safety counts are absolute and never averaged
 * away. Percentiles use nearest-rank over ascending elapsed milliseconds.</p>
 */
public final class PortfolioSelectionBenchmarkEvaluator {
    public PortfolioSelectionBenchmarkReport evaluate(
            String releaseVersion, List<PortfolioSelectionBenchmarkCase> cases,
            List<PortfolioSelectionObservation> observations,
            MigrationIntegrityResult migrationIntegrity) {
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Map<String, PortfolioSelectionBenchmarkCase> byId = new HashMap<>();
        for (PortfolioSelectionBenchmarkCase benchmarkCase : cases) {
            if (byId.putIfAbsent(benchmarkCase.getId(), benchmarkCase) != null) {
                throw new IllegalArgumentException("duplicate benchmark case ID");
            }
        }
        Map<BenchmarkRoute, Map<String, PortfolioSelectionObservation>> byRoute = new EnumMap<>(BenchmarkRoute.class);
        for (PortfolioSelectionObservation observation : observations) {
            if (!releaseVersion.equals(observation.getReleaseVersion()) || !byId.containsKey(observation.getCaseId())) {
                throw new IllegalArgumentException("observation has unknown case or release");
            }
            Map<String, PortfolioSelectionObservation> route = byRoute.computeIfAbsent(
                    observation.getRoute(), ignored -> new HashMap<>());
            if (route.putIfAbsent(observation.getCaseId(), observation) != null) {
                throw new IllegalArgumentException("duplicate route/case observation");
            }
        }
        Map<BenchmarkRoute, RouteBenchmarkMetrics> routes = new EnumMap<>(BenchmarkRoute.class);
        for (BenchmarkRoute route : BenchmarkRoute.values()) {
            routes.put(route, evaluateRoute(cases, byRoute.getOrDefault(route, Map.of()), releaseVersion));
        }
        return new PortfolioSelectionBenchmarkReport(
                releaseVersion, routes, aggregate(routes, observations), migrationIntegrity);
    }

    private RouteBenchmarkMetrics evaluateRoute(
            List<PortfolioSelectionBenchmarkCase> cases,
            Map<String, PortfolioSelectionObservation> observations, String releaseVersion) {
        List<CaseScore> scores = new ArrayList<>();
        int unavailable = 0;
        for (PortfolioSelectionBenchmarkCase benchmarkCase : cases.stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            PortfolioSelectionObservation observation = observations.get(benchmarkCase.getId());
            if (observation == null || observation.getState() != ObservationState.AVAILABLE) {
                unavailable++;
            } else {
                scores.add(score(benchmarkCase, observation, releaseVersion));
            }
        }
        if (scores.isEmpty()) {
            return RouteBenchmarkMetrics.unavailable(cases.size());
        }
        int unsupported = scores.stream().mapToInt(CaseScore::getUnsupported).sum();
        int crossRelease = scores.stream().mapToInt(CaseScore::getCrossRelease).sum();
        int invalidEvidence = scores.stream().mapToInt(CaseScore::getInvalidEvidence).sum();
        int falseSufficient = scores.stream().mapToInt(CaseScore::getFalseSufficient).sum();
        int selected = scores.stream().mapToInt(CaseScore::getSelected).sum();
        List<Long> latencies = scores.stream().map(CaseScore::getLatency).sorted().toList();
        RouteAvailability availability = unavailable == 0
                ? RouteAvailability.AVAILABLE : RouteAvailability.PARTIAL;
        return new RouteBenchmarkMetrics(
                availability, scores.size(), unavailable,
                average(scores, CaseScore::getRecall), average(scores, CaseScore::getHit1),
                average(scores, CaseScore::getHit5), average(scores, CaseScore::getMrr),
                average(scores, CaseScore::getNdcg), average(scores, CaseScore::getCapabilityCoverage),
                average(scores, CaseScore::getRedundancy),
                selected == 0 ? 1.0 : (double) (selected - invalidEvidence) / selected,
                falseSufficient, (double) falseSufficient / scores.size(),
                unsupported, crossRelease, invalidEvidence,
                percentile(latencies, 0.50), percentile(latencies, 0.95));
    }

    private CaseScore score(
            PortfolioSelectionBenchmarkCase benchmarkCase,
            PortfolioSelectionObservation observation, String releaseVersion) {
        Set<String> acceptable = bestSet(
                benchmarkCase.getAcceptableSubjectSets(), observation.getRankedCandidateSubjectIds());
        List<String> ranked = observation.getRankedCandidateSubjectIds();
        int relevantAt12 = countRelevant(ranked, acceptable, 12);
        double recall = (double) relevantAt12 / acceptable.size();
        double hit1 = countRelevant(ranked, acceptable, 1) > 0 ? 1.0 : 0.0;
        double hit5 = countRelevant(ranked, acceptable, 5) > 0 ? 1.0 : 0.0;
        int firstRank = firstRelevantRank(ranked, acceptable);
        double mrr = firstRank == 0 ? 0.0 : 1.0 / firstRank;
        double ndcg = ndcg(ranked, acceptable);

        Set<String> capabilities = new HashSet<>();
        Map<String, Integer> capabilityCounts = new HashMap<>();
        int unsupported = 0;
        int crossRelease = 0;
        int invalidEvidence = 0;
        for (SelectedSubjectObservation subject : observation.getSelectedSubjects()) {
            capabilities.addAll(subject.getCapabilities());
            subject.getCapabilities().forEach(capability ->
                    capabilityCounts.merge(capability, 1, Integer::sum));
            if (!subject.isSupported()
                    || !observation.getRankedCandidateSubjectIds().contains(subject.getSubjectId())) {
                unsupported++;
            }
            if (!subject.isApprovedEvidenceValid()) {
                invalidEvidence++;
            }
            if (!releaseVersion.equals(subject.getReleaseVersion())) {
                crossRelease++;
            }
        }
        long covered = benchmarkCase.getRequiredCapabilities().stream().filter(capabilities::contains).count();
        double coverage = benchmarkCase.getRequiredCapabilities().isEmpty()
                ? 1.0 : (double) covered / benchmarkCase.getRequiredCapabilities().size();
        int occurrences = capabilityCounts.values().stream().mapToInt(Integer::intValue).sum();
        int duplicates = capabilityCounts.values().stream().mapToInt(value -> Math.max(0, value - 1)).sum();
        double redundancy = occurrences == 0 ? 0.0 : (double) duplicates / occurrences;
        long uniqueSelected = observation.getSelectedSubjectIds().stream().distinct().count();
        boolean actuallySufficient = coverage == 1.0
                && observation.getSelectedSubjectIds().size() == benchmarkCase.getTarget().getRequestedSize()
                && uniqueSelected == observation.getSelectedSubjectIds().size()
                && unsupported == 0 && invalidEvidence == 0 && crossRelease == 0;
        return new CaseScore(recall, hit1, hit5, mrr, ndcg, coverage, redundancy,
                observation.isSufficient() && !actuallySufficient ? 1 : 0,
                unsupported, crossRelease, invalidEvidence,
                observation.getSelectedSubjectIds().size(), observation.getElapsedMilliseconds());
    }

    private Set<String> bestSet(List<Set<String>> acceptableSets, List<String> ranked) {
        return acceptableSets.stream()
                .sorted((left, right) -> canonical(left).compareTo(canonical(right)))
                .max((left, right) -> Double.compare(ndcg(ranked, left), ndcg(ranked, right)))
                .orElseThrow();
    }

    private String canonical(Set<String> values) {
        return String.join("\u0000", values.stream().sorted().toList());
    }

    private int countRelevant(List<String> ranked, Set<String> relevant, int limit) {
        return (int) ranked.stream().limit(limit).distinct().filter(relevant::contains).count();
    }

    private int firstRelevantRank(List<String> ranked, Set<String> relevant) {
        for (int index = 0; index < ranked.size(); index++) {
            if (relevant.contains(ranked.get(index))) {
                return index + 1;
            }
        }
        return 0;
    }

    private double ndcg(List<String> ranked, Set<String> relevant) {
        double dcg = 0.0;
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < Math.min(12, ranked.size()); index++) {
            String subject = ranked.get(index);
            if (seen.add(subject) && relevant.contains(subject)) {
                dcg += 1.0 / log2(index + 2);
            }
        }
        double ideal = 0.0;
        for (int index = 0; index < Math.min(12, relevant.size()); index++) {
            ideal += 1.0 / log2(index + 2);
        }
        return ideal == 0.0 ? 0.0 : dcg / ideal;
    }

    private double log2(int value) {
        return Math.log(value) / Math.log(2.0);
    }

    private double average(List<CaseScore> scores, Metric metric) {
        return scores.stream().mapToDouble(metric::value).average().orElse(Double.NaN);
    }

    private long percentile(List<Long> values, double percentile) {
        int rank = Math.max(1, (int) Math.ceil(percentile * values.size()));
        return values.get(rank - 1);
    }

    private RouteBenchmarkMetrics aggregate(
            Map<BenchmarkRoute, RouteBenchmarkMetrics> routes,
            List<PortfolioSelectionObservation> observations) {
        List<RouteBenchmarkMetrics> available = routes.values().stream()
                .filter(value -> value.getEvaluatedCaseCount() > 0).toList();
        if (available.isEmpty()) {
            return RouteBenchmarkMetrics.unavailable(
                    routes.values().stream().mapToInt(RouteBenchmarkMetrics::getUnavailableCaseCount).sum());
        }
        int evaluated = available.stream().mapToInt(RouteBenchmarkMetrics::getEvaluatedCaseCount).sum();
        int unavailable = routes.values().stream()
                .mapToInt(RouteBenchmarkMetrics::getUnavailableCaseCount).sum();
        int selected = observations.stream()
                .filter(value -> value.getState() == ObservationState.AVAILABLE)
                .mapToInt(value -> value.getSelectedSubjectIds().size()).sum();
        int invalidEvidence = available.stream().mapToInt(RouteBenchmarkMetrics::getInvalidEvidenceCount).sum();
        List<Long> latencies = observations.stream()
                .filter(value -> value.getState() == ObservationState.AVAILABLE)
                .map(PortfolioSelectionObservation::getElapsedMilliseconds).sorted().toList();
        return new RouteBenchmarkMetrics(
                unavailable == 0 ? RouteAvailability.AVAILABLE : RouteAvailability.PARTIAL,
                evaluated, unavailable,
                weighted(available, RouteBenchmarkMetrics::getRecallAt12, evaluated),
                weighted(available, RouteBenchmarkMetrics::getHitAt1, evaluated),
                weighted(available, RouteBenchmarkMetrics::getHitAt5, evaluated),
                weighted(available, RouteBenchmarkMetrics::getMeanReciprocalRank, evaluated),
                weighted(available, RouteBenchmarkMetrics::getNormalizedDiscountedCumulativeGain, evaluated),
                weighted(available, RouteBenchmarkMetrics::getCapabilityCoverage, evaluated),
                weighted(available, RouteBenchmarkMetrics::getRedundancy, evaluated),
                selected == 0 ? 1.0 : (double) (selected - invalidEvidence) / selected,
                available.stream().mapToInt(RouteBenchmarkMetrics::getFalseSufficientCount).sum(),
                (double) available.stream().mapToInt(RouteBenchmarkMetrics::getFalseSufficientCount).sum()
                        / evaluated,
                available.stream().mapToInt(RouteBenchmarkMetrics::getUnsupportedRecommendationCount).sum(),
                available.stream().mapToInt(RouteBenchmarkMetrics::getCrossReleaseMixCount).sum(),
                invalidEvidence, percentile(latencies, 0.50), percentile(latencies, 0.95));
    }

    private double weighted(
            List<RouteBenchmarkMetrics> values,
            java.util.function.ToDoubleFunction<RouteBenchmarkMetrics> metric,
            int denominator) {
        return values.stream()
                .mapToDouble(value -> metric.applyAsDouble(value) * value.getEvaluatedCaseCount())
                .sum() / denominator;
    }

    private interface Metric {
        double value(CaseScore score);
    }

    private static final class CaseScore {
        private final double recall;
        private final double hit1;
        private final double hit5;
        private final double mrr;
        private final double ndcg;
        private final double capabilityCoverage;
        private final double redundancy;
        private final int falseSufficient;
        private final int unsupported;
        private final int crossRelease;
        private final int invalidEvidence;
        private final int selected;
        private final long latency;

        private CaseScore(
                double recall, double hit1, double hit5, double mrr, double ndcg,
                double capabilityCoverage, double redundancy, int falseSufficient,
                int unsupported, int crossRelease, int invalidEvidence, int selected, long latency) {
            this.recall = recall; this.hit1 = hit1; this.hit5 = hit5; this.mrr = mrr;
            this.ndcg = ndcg; this.capabilityCoverage = capabilityCoverage;
            this.redundancy = redundancy; this.falseSufficient = falseSufficient;
            this.unsupported = unsupported; this.crossRelease = crossRelease;
            this.invalidEvidence = invalidEvidence; this.selected = selected; this.latency = latency;
        }

        private double getRecall() { return recall; }
        private double getHit1() { return hit1; }
        private double getHit5() { return hit5; }
        private double getMrr() { return mrr; }
        private double getNdcg() { return ndcg; }
        private double getCapabilityCoverage() { return capabilityCoverage; }
        private double getRedundancy() { return redundancy; }
        private int getFalseSufficient() { return falseSufficient; }
        private int getUnsupported() { return unsupported; }
        private int getCrossRelease() { return crossRelease; }
        private int getInvalidEvidence() { return invalidEvidence; }
        private int getSelected() { return selected; }
        private long getLatency() { return latency; }
    }
}
