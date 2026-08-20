package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.grading.EvalGrade;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class EvalMetricAggregator {

    private static final Set<String> PRIORITY_METRICS = Set.of(
            "routing.top1",
            "safety.boundaryPassRate");

    public EvalMetrics aggregate(
            List<EvalGrade> grades,
            List<EvalObservation> observations,
            int publicSubjectCount,
            int coveredSubjectCount) {
        Objects.requireNonNull(grades, "grades");
        Objects.requireNonNull(observations, "observations");
        Map<String, EvalMetrics.MetricValue> metrics = new LinkedHashMap<>();

        long caseCount = grades.stream()
                .map(EvalGrade::getCaseId).distinct().count();
        long executedCaseCount = observations.stream()
                .map(EvalObservation::getCaseId).distinct().count();
        long skippedCaseCount = observations.stream()
                .filter(observation ->
                        observation.getStatus().name().equals("SKIPPED"))
                .map(EvalObservation::getCaseId).distinct().count();

        metrics.put("run.caseCount", count(caseCount));
        metrics.put("run.executedCaseCount", count(executedCaseCount));
        metrics.put("run.skippedCaseCount", count(skippedCaseCount));

        metrics.put("content.smokeCoverage", ratio(
                coveredSubjectCount, publicSubjectCount));

        rate(metrics, grades, "routing.top1",
                EvalLayer.HTTP_E2E, "SUBJECT_MATCH");
        rate(metrics, grades, "retrieval.claimRecall",
                null, "REQUIRED_CLAIMS");
        rate(metrics, grades, "answer.answerRate",
                null, "RESOLUTION");
        rate(metrics, grades, "safety.boundaryPassRate",
                null, "FORBIDDEN_SUBJECT");
        rate(metrics, grades, "api.contractPassRate",
                null, "API_CONTRACT");
        rate(metrics, grades, "structure.answerQualityPassRate",
                null, "ANSWER_QUALITY");
        rate(metrics, grades, "semantic.turnStructurePassRate",
                null, "SEMANTIC_TURN_STRUCTURE");

        long hardErrors = grades.stream()
                .filter(grade -> !grade.isPassed()
                        && grade.getSeverity() == EvalSeverity.BLOCKING)
                .count();
        long fakeCitations = countReason(grades, EvalReasonCode.FAKE_CITATION);
        long falseSufficient = countReason(grades, EvalReasonCode.FALSE_SUFFICIENT);
        long executorErrors = countReason(grades, EvalReasonCode.EXECUTOR_ERROR);
        metrics.put("hardError.count", count(hardErrors));
        metrics.put("hardError.fakeCitation", count(fakeCitations));
        metrics.put("hardError.falseSufficient", count(falseSufficient));
        metrics.put("hardError.executorError", count(executorErrors));

        long providerPassed = grades.stream()
                .filter(grade -> grade.getLayer() == EvalLayer.PROVIDER
                        && grade.isPassed())
                .count();
        long providerFailures = grades.stream()
                .filter(grade -> grade.getLayer() == EvalLayer.PROVIDER
                        && !grade.isPassed())
                .count();
        long providerTotal = grades.stream()
                .filter(grade -> grade.getLayer() == EvalLayer.PROVIDER)
                .count();
        metrics.put("provider.trialPassRate", ratio(providerPassed, providerTotal));
        metrics.put("provider.errorRate", ratio(providerFailures, providerTotal));

        List<EvalObservation> providerObservations = observations.stream()
                .filter(observation -> observation.getLayer() == EvalLayer.PROVIDER)
                .toList();
        if (!providerObservations.isEmpty()) {
            long totalLatency = providerObservations.stream()
                    .mapToLong(EvalObservation::getDurationMilliseconds)
                    .sum();
            long p95 = percentile95(providerObservations);
            metrics.put("provider.p95LatencyMs",
                    new EvalMetrics.MetricValue(
                            BigDecimal.valueOf(p95), totalLatency, providerObservations.size()));
        }
        return new EvalMetrics(metrics);
    }

    private long countReason(List<EvalGrade> grades, EvalReasonCode reasonCode) {
        return grades.stream()
                .filter(grade -> grade.getReasonCode() == reasonCode)
                .count();
    }

    private void rate(
            Map<String, EvalMetrics.MetricValue> metrics,
            List<EvalGrade> grades,
            String name,
            EvalLayer layer,
            String graderType) {
        long denominator = grades.stream()
                .filter(grade -> (layer == null || grade.getLayer() == layer)
                        && grade.getGraderType().equals(graderType))
                .count();
        long numerator = grades.stream()
                .filter(grade -> (layer == null || grade.getLayer() == layer)
                        && grade.getGraderType().equals(graderType)
                        && grade.isPassed())
                .count();
        metrics.put(name, ratio(numerator, denominator));
    }

    private EvalMetrics.MetricValue ratio(long numerator, long denominator) {
        if (denominator == 0L) {
            return new EvalMetrics.MetricValue(BigDecimal.ZERO, 0L, 0L);
        }
        BigDecimal value = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
        return new EvalMetrics.MetricValue(value, numerator, denominator);
    }

    private EvalMetrics.MetricValue count(long value) {
        return new EvalMetrics.MetricValue(BigDecimal.valueOf(value), value, 1L);
    }

    private long percentile95(List<EvalObservation> observations) {
        List<Long> durations = observations.stream()
                .map(EvalObservation::getDurationMilliseconds)
                .sorted()
                .collect(Collectors.toList());
        if (durations.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(0.95 * durations.size()) - 1);
        return durations.get(Math.min(index, durations.size() - 1));
    }
}
