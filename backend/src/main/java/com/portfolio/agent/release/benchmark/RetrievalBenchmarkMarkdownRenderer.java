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
        appendRunMetadata(markdown, report);
        appendSplitMetrics(
                markdown, report, RetrievalBenchmarkSplit.HOLDOUT, "Holdout");
        appendSplitMetrics(
                markdown,
                report,
                RetrievalBenchmarkSplit.CALIBRATION,
                "Calibration"
        );
        appendFailures(markdown, report);
        return markdown.toString();
    }

    private void appendRunMetadata(
            StringBuilder markdown,
            RetrievalBenchmarkReport report
    ) {
        RetrievalBenchmarkRunMetadata metadata = report.getRunMetadata();
        if (metadata == null) {
            return;
        }
        markdown.append("## Run metadata\n\n");
        markdown.append("- Java: `").append(metadata.getJavaVersion())
                .append("` / `").append(metadata.getJavaRuntimeName())
                .append("` / `").append(metadata.getJavaVendor())
                .append("`\n");
        markdown.append("- OS: `").append(metadata.getOsName())
                .append("` / `").append(metadata.getOsVersion())
                .append("` / `").append(metadata.getOsArch())
                .append("`\n");
        markdown.append("- Available processors: `")
                .append(metadata.getAvailableProcessors()).append("`\n");
        markdown.append("- Started at: `").append(metadata.getStartedAt())
                .append("`\n");
        markdown.append("- Completed at: `").append(metadata.getCompletedAt())
                .append("`\n");
        markdown.append("- Duration millis: `")
                .append(metadata.getDurationMillis()).append("`\n\n");
    }

    private void appendSplitMetrics(
            StringBuilder markdown,
            RetrievalBenchmarkReport report,
            RetrievalBenchmarkSplit split,
            String label
    ) {
        markdown.append("## ").append(label).append(" route metrics\n\n");
        markdown.append("| Route | Positive cases | Hit@1 | Hit@5 | MRR@5"
                + " | Positive decision success | Positive decision success rate"
                + " | False sufficient |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RetrievalBenchmarkGroupMetrics group
                : report.getSplitRouteMetrics()) {
            if (group.getSplit() == split) {
                appendMetricsRow(
                        markdown,
                        group.getRoute().name(),
                        group.getMetrics()
                );
            }
        }
        markdown.append('\n');

        markdown.append("### ").append(label)
                .append(" category breakdown\n\n");
        markdown.append("| Category | Route | Positive cases | Hit@1 | Hit@5 | MRR@5"
                + " | Positive decision success | Positive decision success rate"
                + " | False sufficient |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RetrievalBenchmarkGroupMetrics group
                : report.getSplitCategoryRouteMetrics()) {
            if (group.getSplit() != split) {
                continue;
            }
            RetrievalBenchmarkMetrics metrics = group.getMetrics();
            markdown.append("| ").append(group.getCategory().name())
                    .append(" | ").append(group.getRoute().name())
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

        markdown.append("### ").append(label)
                .append(" decision distribution\n\n");
        markdown.append("| Route | Actual decision | Count |\n");
        markdown.append("| --- | --- | ---: |\n");
        for (RetrievalDecisionCount count
                : report.getSplitRouteDecisionCounts()) {
            if (count.getSplit() == split) {
                markdown.append("| ").append(count.getRoute().name())
                        .append(" | ")
                        .append(count.getActualDecision().name())
                        .append(" | ").append(count.getCount())
                        .append(" |\n");
            }
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
