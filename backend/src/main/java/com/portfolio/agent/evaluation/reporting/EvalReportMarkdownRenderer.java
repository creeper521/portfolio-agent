package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.grading.EvalGrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Markdown report derived only from the canonical JSON fact source fields.
 */
public final class EvalReportMarkdownRenderer {

    public String render(EvalRunReport report, boolean challengeMode) {
        StringBuilder out = new StringBuilder();
        out.append("# 评测报告\n\n");
        out.append("- 运行 ID：").append(report.getRunId()).append('\n');
        out.append("- 模式：").append(report.getMode().name()).append('\n');
        out.append("- 数据集版本：").append(report.getIdentity().getDatasetVersion()).append('\n');
        out.append("- 数据集哈希：").append(report.getIdentity().getDatasetHash()).append('\n');
        out.append("- Bundle 版本：").append(report.getIdentity().getBundleVersion()).append('\n');
        out.append("- Bundle 哈希：").append(report.getIdentity().getBundleHash()).append('\n');
        out.append("- 样本数：")
                .append(report.getMetrics().getValue("run.caseCount").getValue())
                .append('\n');
        out.append("- 结论：**").append(report.getVerdict().name()).append("**\n\n");

        if (challengeMode) {
            out.append("### 聚合指标\n\n");
            appendAggregates(out, report);
            return out.toString();
        }

        out.append("## 身份\n\n");
        out.append("| 字段 | 值 |\n|---|---|\n");
        out.append("| gitCommit | ").append(report.getIdentity().getGitCommit()).append(" |\n");
        out.append("| datasetVersion | ").append(report.getIdentity().getDatasetVersion())
                .append(" |\n");
        out.append("| bundleVersion | ").append(report.getIdentity().getBundleVersion())
                .append(" |\n");
        out.append("| provider | ").append(report.getIdentity().getProvider()).append(" |\n");
        out.append("| model | ").append(report.getIdentity().getModel()).append(" |\n");
        out.append("| judgeModel | ").append(report.getIdentity().getJudgeModel())
                .append(" |\n\n");

        out.append("## 门禁\n\n");
        out.append("| 指标 | 观察值 | 阈值 | 比较 | 通过 | 严重度 | 原因 |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        List<EvalGateResult> gates = new ArrayList<>(report.getGates());
        gates.sort(Comparator.comparing(EvalGateResult::getMetricName));
        for (EvalGateResult gate : gates) {
            out.append("| ").append(gate.getMetricName())
                    .append(" | ").append(gate.getObserved())
                    .append(" | ").append(gate.getThreshold())
                    .append(" | ").append(gate.getComparison().name())
                    .append(" | ").append(gate.isPassed())
                    .append(" | ").append(gate.getSeverity().name())
                    .append(" | ").append(gate.getReasonCode().name())
                    .append(" |\n");
        }
        out.append('\n');

        out.append("## 失败 Case\n\n");
        List<String> failedCases = new ArrayList<>();
        for (EvalGrade grade : report.getGrades()) {
            if (!grade.isPassed()) {
                failedCases.add(grade.getCaseId());
            }
        }
        if (failedCases.isEmpty()) {
            out.append("无\n\n");
        } else {
            failedCases.stream().distinct().sorted().forEach(caseId ->
                    out.append("- ").append(caseId).append('\n'));
            out.append('\n');
        }

        out.append("## 跳过项\n\n");
        List<String> skipped = new ArrayList<>();
        for (EvalObservation observation : report.getObservations()) {
            if (observation.getStatus() == EvalObservationStatus.SKIPPED) {
                skipped.add(observation.getCaseId());
            }
        }
        if (skipped.isEmpty()) {
            out.append("无\n\n");
        } else {
            skipped.stream().distinct().sorted().forEach(caseId ->
                    out.append("- ").append(caseId).append('\n'));
            out.append('\n');
        }

        out.append("## 结构观察指标\n\n");
        appendAggregates(out, report);
        return out.toString();
    }

    private void appendAggregates(StringBuilder out, EvalRunReport report) {
        out.append("| 指标 | 值 | 分子/分母 |\n|---|---|---|\n");
        Map<String, EvalMetrics.MetricValue> values = report.getMetrics().getAll();
        List<String> names = new ArrayList<>(values.keySet());
        names.sort(String::compareTo);
        for (String name : names) {
            EvalMetrics.MetricValue value = values.get(name);
            out.append("| ").append(name)
                    .append(" | ").append(value.getValue())
                    .append(" | ").append(value.getNumerator())
                    .append('/').append(value.getDenominator())
                    .append(" |\n");
        }
    }
}
