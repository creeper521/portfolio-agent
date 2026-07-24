package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RetrievalBenchmarkReport {

    private final String suiteVersion;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final String snapshotValidFrom;
    private final String policyVersion;
    private final String modelDescriptorHash;
    private final List<Evaluation> evaluations;
    private final Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metricsByRoute;
    private final List<CategoryMetrics> categoryMetrics;

    public RetrievalBenchmarkReport(
            String suiteVersion,
            String contentVersion,
            String runtimeBundleHash,
            String snapshotValidFrom,
            String policyVersion,
            String modelDescriptorHash,
            List<RetrievalRouteEvaluation> evaluations,
            Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metricsByRoute
    ) {
        this.suiteVersion = Objects.requireNonNull(suiteVersion, "suiteVersion");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.runtimeBundleHash = Objects.requireNonNull(
                runtimeBundleHash,
                "runtimeBundleHash"
        );
        this.snapshotValidFrom = Objects.requireNonNull(
                snapshotValidFrom,
                "snapshotValidFrom"
        );
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
        this.modelDescriptorHash = Objects.requireNonNull(modelDescriptorHash, "modelDescriptorHash");
        this.evaluations = safeEvaluations(evaluations);
        this.metricsByRoute = stableMetrics(metricsByRoute);
        this.categoryMetrics = categoryMetrics(this.evaluations);
    }

    public String getSuiteVersion() { return suiteVersion; }
    public String getContentVersion() { return contentVersion; }
    public String getRuntimeBundleHash() { return runtimeBundleHash; }
    public String getSnapshotValidFrom() { return snapshotValidFrom; }
    public String getPolicyVersion() { return policyVersion; }
    public String getModelDescriptorHash() { return modelDescriptorHash; }
    public List<Evaluation> getEvaluations() { return evaluations; }
    public Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> getMetricsByRoute() { return metricsByRoute; }
    public List<CategoryMetrics> getCategoryMetrics() { return categoryMetrics; }

    private List<Evaluation> safeEvaluations(List<RetrievalRouteEvaluation> source) {
        Objects.requireNonNull(source, "evaluations");
        List<Evaluation> result = new ArrayList<>();
        for (RetrievalRouteEvaluation evaluation : source) {
            result.add(new Evaluation(evaluation));
        }
        result.sort(Comparator.comparing(Evaluation::getRoute).thenComparing(Evaluation::getCaseId));
        return List.copyOf(result);
    }

    private Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> stableMetrics(
            Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> source
    ) {
        Objects.requireNonNull(source, "metricsByRoute");
        Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> result =
                new EnumMap<>(RetrievalBenchmarkRoute.class);
        for (RetrievalBenchmarkRoute route : RetrievalBenchmarkRoute.values()) {
            RetrievalBenchmarkMetrics metrics = source.get(route);
            result.put(route, metrics == null ? zeroMetrics() : metrics);
        }
        return Collections.unmodifiableMap(result);
    }

    private List<CategoryMetrics> categoryMetrics(List<Evaluation> safeEvaluations) {
        List<CategoryMetrics> result = new ArrayList<>();
        RetrievalBenchmarkEvaluator evaluator = new RetrievalBenchmarkEvaluator();
        for (RetrievalBenchmarkCategory category : RetrievalBenchmarkCategory.values()) {
            for (RetrievalBenchmarkRoute route : RetrievalBenchmarkRoute.values()) {
                List<RetrievalRouteEvaluation> matching = new ArrayList<>();
                for (Evaluation evaluation : safeEvaluations) {
                    if (evaluation.getCategory() == category && evaluation.getRoute() == route) {
                        matching.add(evaluation.toRouteEvaluation());
                    }
                }
                if (!matching.isEmpty()) {
                    RetrievalBenchmarkMetrics metrics = evaluator.evaluate(matching).get(route);
                    result.add(new CategoryMetrics(category, route, metrics));
                }
            }
        }
        return List.copyOf(result);
    }

    private RetrievalBenchmarkMetrics zeroMetrics() {
        return new RetrievalBenchmarkMetrics(
                0,
                0.0,
                0.0,
                0.0,
                0,
                0.0,
                0
        );
    }

    public static final class Evaluation {

        private final RetrievalBenchmarkRoute route;
        private final String caseId;
        private final RetrievalBenchmarkSplit split;
        private final RetrievalBenchmarkCategory category;
        private final RetrievalDecisionType expectedDecision;
        private final RetrievalDecisionType actualDecision;
        private final Integer expectedRank;

        private Evaluation(RetrievalRouteEvaluation source) {
            this.route = source.getRoute();
            this.caseId = source.getCaseId();
            this.split = source.getSplit();
            this.category = source.getCategory();
            this.expectedDecision = source.getExpectedDecision();
            this.actualDecision = source.getActualDecision();
            this.expectedRank = source.getExpectedRank();
        }

        public RetrievalBenchmarkRoute getRoute() { return route; }
        public String getCaseId() { return caseId; }
        public RetrievalBenchmarkSplit getSplit() { return split; }
        public RetrievalBenchmarkCategory getCategory() { return category; }
        public RetrievalDecisionType getExpectedDecision() { return expectedDecision; }
        public RetrievalDecisionType getActualDecision() { return actualDecision; }
        public Integer getExpectedRank() { return expectedRank; }

        public boolean isFailure() {
            return expectedDecision != actualDecision
                    || (expectedDecision == RetrievalDecisionType.SUFFICIENT
                    && (expectedRank == null || expectedRank > 5));
        }

        private RetrievalRouteEvaluation toRouteEvaluation() {
            return new RetrievalRouteEvaluation(
                    route,
                    caseId,
                    split,
                    category,
                    expectedDecision,
                    actualDecision,
                    expectedRank,
                    List.of(),
                    List.of()
            );
        }
    }

    public static final class CategoryMetrics {

        private final RetrievalBenchmarkCategory category;
        private final RetrievalBenchmarkRoute route;
        private final RetrievalBenchmarkMetrics metrics;

        private CategoryMetrics(
                RetrievalBenchmarkCategory category,
                RetrievalBenchmarkRoute route,
                RetrievalBenchmarkMetrics metrics
        ) {
            this.category = category;
            this.route = route;
            this.metrics = metrics;
        }

        public RetrievalBenchmarkCategory getCategory() { return category; }
        public RetrievalBenchmarkRoute getRoute() { return route; }
        public RetrievalBenchmarkMetrics getMetrics() { return metrics; }
    }
}
