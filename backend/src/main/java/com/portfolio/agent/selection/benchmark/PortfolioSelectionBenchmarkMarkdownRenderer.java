package com.portfolio.agent.selection.benchmark;

import java.util.Locale;

public final class PortfolioSelectionBenchmarkMarkdownRenderer {
    public String render(PortfolioSelectionBenchmarkReport report) {
        StringBuilder markdown = new StringBuilder()
                .append("# Portfolio selection benchmark\n\n")
                .append("- Release: `").append(report.getReleaseVersion()).append("`\n")
                .append("- No R0–R4 improvement is claimed until every route is measured ")
                .append("against the same release, model, and holdout.\n\n")
                .append("- PARTIAL rows include unavailable cases and are not comparable ")
                .append("with complete routes.\n\n")
                .append("| Route | State | Cases | Recall@12 | Hit@1 | Hit@5 | MRR | nDCG@12 | ")
                .append("Capability coverage | Redundancy | Evidence validity | ")
                .append("False sufficient | Unsupported | Cross release | p50 ms | p95 ms |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (BenchmarkRoute route : BenchmarkRoute.values()) {
            RouteBenchmarkMetrics metrics = report.getRoutes().get(route);
            markdown.append("| ").append(route).append(" | ").append(metrics.getAvailability())
                    .append(" | ").append(metrics.getEvaluatedCaseCount())
                    .append(metrics.getUnavailableCaseCount() == 0
                            ? "" : " (+" + metrics.getUnavailableCaseCount() + " unavailable)")
                    .append(" | ").append(value(metrics.getRecallAt12()))
                    .append(" | ").append(value(metrics.getHitAt1()))
                    .append(" | ").append(value(metrics.getHitAt5()))
                    .append(" | ").append(value(metrics.getMeanReciprocalRank()))
                    .append(" | ").append(value(metrics.getNormalizedDiscountedCumulativeGain()))
                    .append(" | ").append(value(metrics.getCapabilityCoverage()))
                    .append(" | ").append(value(metrics.getRedundancy()))
                    .append(" | ").append(value(metrics.getApprovedEvidenceValidityRate()))
                    .append(" | ").append(metrics.getFalseSufficientCount())
                    .append(" | ").append(metrics.getUnsupportedRecommendationCount())
                    .append(" | ").append(metrics.getCrossReleaseMixCount())
                    .append(" | ").append(latency(metrics.getP50LatencyMilliseconds()))
                    .append(" | ").append(latency(metrics.getP95LatencyMilliseconds())).append(" |\n");
        }
        return markdown.toString();
    }

    private String value(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.4f", value) : "UNAVAILABLE";
    }

    private String latency(Long value) {
        return value == null ? "—" : value.toString();
    }
}
