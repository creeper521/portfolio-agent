package com.portfolio.agent.release.benchmark;

import java.util.Locale;

public final class RetrievalBenchmarkMarkdownRenderer {

    public String render(RetrievalBenchmarkReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Retrieval Baseline Comparison\n\n");
        markdown.append("## Immutable identities\n\n");
        markdown.append("- Suite version: `").append(report.getSuiteVersion()).append("`\n");
        markdown.append("- Content version: `").append(report.getContentVersion()).append("`\n");
        markdown.append("- Verified runtime Bundle hash: `")
                .append(report.getRuntimeBundleHash()).append("`\n");
        markdown.append("- Snapshot validFrom: `")
                .append(report.getSnapshotValidFrom()).append("`\n");
        markdown.append("- Policy version: `").append(report.getPolicyVersion()).append("`\n");
        markdown.append("- Model descriptor hash: `").append(report.getModelDescriptorHash()).append("`\n\n");
        appendRouteMetrics(markdown, report);
        appendCategoryMetrics(markdown, report);
        appendFailures(markdown, report);
        return markdown.toString();
    }

    private void appendRouteMetrics(StringBuilder markdown, RetrievalBenchmarkReport report) {
        markdown.append("## Route metrics\n\n");
        markdown.append("| Route | Positive cases | Hit@1 | Hit@5 | MRR@5"
                + " | Positive decision success | Positive decision success rate"
                + " | False sufficient |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RetrievalBenchmarkRoute route : RetrievalBenchmarkRoute.values()) {
            appendMetricsRow(markdown, route.name(), report.getMetricsByRoute().get(route));
        }
        markdown.append('\n');
    }

    private void appendCategoryMetrics(StringBuilder markdown, RetrievalBenchmarkReport report) {
        markdown.append("## Category breakdown\n\n");
        markdown.append("| Category | Route | Positive cases | Hit@1 | Hit@5 | MRR@5"
                + " | Positive decision success | Positive decision success rate"
                + " | False sufficient |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RetrievalBenchmarkReport.CategoryMetrics categoryMetrics : report.getCategoryMetrics()) {
            RetrievalBenchmarkMetrics metrics = categoryMetrics.getMetrics();
            markdown.append("| ").append(categoryMetrics.getCategory().name())
                    .append(" | ").append(categoryMetrics.getRoute().name())
                    .append(" | ").append(metrics.getPositiveCount())
                    .append(" | ").append(metric(metrics.getHitAt1()))
                    .append(" | ").append(metric(metrics.getHitAt5()))
                    .append(" | ").append(metric(metrics.getMrrAt5()))
                    .append(" | ").append(
                            metrics.getPositiveDecisionSuccessCount())
                    .append(" | ").append(metric(
                            metrics.getPositiveDecisionSuccessRate()))
                    .append(" | ").append(metrics.getFalseSufficientCount())
                    .append(" |\n");
        }
        markdown.append('\n');
    }

    private void appendFailures(StringBuilder markdown, RetrievalBenchmarkReport report) {
        markdown.append("## Failing cases\n\n");
        boolean hasFailure = false;
        for (RetrievalBenchmarkReport.Evaluation evaluation : report.getEvaluations()) {
            if (evaluation.isFailure()) {
                markdown.append("- ").append(evaluation.getRoute().name())
                        .append(": `").append(evaluation.getCaseId()).append("`\n");
                hasFailure = true;
            }
        }
        if (!hasFailure) {
            markdown.append("- None\n");
        }
    }

    private void appendMetricsRow(StringBuilder markdown, String label, RetrievalBenchmarkMetrics metrics) {
        markdown.append("| ").append(label)
                .append(" | ").append(metrics.getPositiveCount())
                .append(" | ").append(metric(metrics.getHitAt1()))
                .append(" | ").append(metric(metrics.getHitAt5()))
                .append(" | ").append(metric(metrics.getMrrAt5()))
                .append(" | ").append(metrics.getPositiveDecisionSuccessCount())
                .append(" | ").append(metric(
                        metrics.getPositiveDecisionSuccessRate()))
                .append(" | ").append(metrics.getFalseSufficientCount())
                .append(" |\n");
    }

    private String metric(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
