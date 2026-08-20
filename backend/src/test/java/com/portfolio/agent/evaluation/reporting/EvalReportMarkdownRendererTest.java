package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.common.observability.GenerationMode;
import com.portfolio.agent.evaluation.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.grading.EvalGrade;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportMarkdownRendererTest {

    private final EvalReportMarkdownRenderer renderer = new EvalReportMarkdownRenderer();

    @Test
    void markdownDerivesVerdictIdentityGatesFailuresAndSkipsFromJsonFactSource() {
        String markdown = renderer.render(reportFixture(), false);

        assertThat(markdown).contains("**PASS**");
        assertThat(markdown).contains("2026-08-06.1");
        assertThat(markdown).contains("routing.top1");
        assertThat(markdown).contains("case-fail");
        assertThat(markdown).contains("case-skipped");
        assertThat(markdown).contains("hardError.count");
        assertThat(markdown).contains("1/2");
    }

    @Test
    void challengeModeShowsOnlyVersionHashesSampleCountAndAggregates() {
        String markdown = renderer.render(reportFixture(), true);

        assertThat(markdown).contains("数据集版本：2026-08-06.1");
        assertThat(markdown).contains("数据集哈希：sha256:dataset");
        assertThat(markdown).contains("样本数：3");
        assertThat(markdown).contains("聚合指标");
        assertThat(markdown).doesNotContain("## 身份");
        assertThat(markdown).doesNotContain("## 门禁");
        assertThat(markdown).doesNotContain("## 失败 Case");
    }

    private EvalRunReport reportFixture() {
        EvalRunIdentity identity = EvalRunIdentity.create(
                "deadbeef", "2026-08-06.1", "sha256:dataset",
                "2026-08-05.1", "sha256:bundle", "sha256:prompt",
                "sha256:retrieval", "BGE-small-zh-v1.5", "sha256:embedding",
                "NOT_APPLICABLE", "NOT_APPLICABLE", "sha256:model-params",
                "NOT_APPLICABLE", "NOT_APPLICABLE");
        EvalObservation passed = new EvalObservation(
                "case-a", EvalLayer.HTTP_E2E, 1, EvalObservationStatus.PASS,
                null, "case-a", List.of("claim-1"), List.of("E-01"), List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of("DETERMINISTIC"), 12L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
        EvalObservation skipped = new EvalObservation(
                "case-skipped", EvalLayer.HTTP_E2E, 1, EvalObservationStatus.SKIPPED,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
        EvalGrade failing = new EvalGrade(
                "case-fail", EvalLayer.HTTP_E2E, 1, "SUBJECT_MATCH",
                EvalSeverity.BLOCKING, false, EvalReasonCode.SUBJECT_MISMATCH, 1L, 2L);
        EvalGateResult gate = new EvalGateResult(
                "routing.top1", new BigDecimal("0.5"), new BigDecimal("0.9"),
                EvalGateResult.EvalComparisonOperator.GE, false, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        EvalMetrics metrics = new EvalMetrics(Map.of(
                "routing.top1", new EvalMetrics.MetricValue(
                        new BigDecimal("0.5"), 1L, 2L),
                "run.caseCount", new EvalMetrics.MetricValue(
                        new BigDecimal("3"), 3L, 1L),
                "hardError.count", new EvalMetrics.MetricValue(
                        BigDecimal.ONE, 1L, 1L)));
        EvalComparison comparison = new EvalComparison(
                false, Map.of(), List.of(), List.of());
        return new EvalRunReport(
                "run-test", EvalRunMode.OFFLINE, identity, EvalVerdict.PASS,
                metrics, comparison, List.of(gate), List.of(passed, skipped),
                List.of(failing), Optional.empty(),
                com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED);
    }
}
