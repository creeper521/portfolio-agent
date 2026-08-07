package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalBaselineComparatorTest {

    private final EvalBaselineComparator comparator = new EvalBaselineComparator();

    @Test
    void incompatibleDatasetIdentityReturnsNotComparableWithoutDeltas() {
        EvalRunIdentity current = identity("ds-v2", "sha256:new", "bundle-v2", "sha256:b",
                "DEEPSEEK_V4_FLASH", "deepseek-v4-flash", "judge-v2", "rubric-v2");
        EvalBaseline baseline = new EvalBaseline(
                "phase-0-answer-composition",
                identity("ds-v1", "sha256:old", "bundle-v2", "sha256:b",
                        "DEEPSEEK_V4_FLASH", "deepseek-v4-flash", "judge-v2", "rubric-v2"),
                Map.of("routing.top1", new BigDecimal("0.9")),
                List.of("case-0"));

        EvalMetrics currentMetrics = new EvalMetrics(Map.of());

        EvalComparison comparison = comparator.compare(
                current, currentMetrics, baseline, List.of("case-1"));

        assertThat(comparison.isComparable()).isFalse();
        assertThat(comparison.getReasonCode())
                .isEqualTo("IDENTITY_NOT_COMPARABLE");
        assertThat(comparison.getDeltas()).isEmpty();
    }

    @Test
    void compatibleIdentityComparesSharedCasesAndTracksAddedRemoved() {
        EvalRunIdentity current = identity("ds-v2", "sha256:new", "bundle-v2", "sha256:b",
                "DEEPSEEK_V4_FLASH", "deepseek-v4-flash", "judge-v2", "rubric-v2");
        EvalRunIdentity baselineIdentity = identity("ds-v2", "sha256:new", "bundle-v2", "sha256:b",
                "DEEPSEEK_V4_FLASH", "deepseek-v4-flash", "judge-v2", "rubric-v2");
        EvalBaseline baseline = new EvalBaseline(
                "phase-0-answer-composition",
                baselineIdentity,
                Map.of("routing.top1", new BigDecimal("0.9")),
                List.of("case-1"));

        EvalMetrics currentMetrics = new EvalMetrics(Map.of(
                "routing.top1", new EvalMetrics.MetricValue(new BigDecimal("0.8"), 4L, 5L)));

        EvalComparison comparison = comparator.compare(
                current, currentMetrics, baseline, List.of("case-1", "case-2", "case-3"));

        assertThat(comparison.isComparable()).isTrue();
        assertThat(comparison.getAddedCaseIds()).containsExactly("case-2", "case-3");
        assertThat(comparison.getRemovedCaseIds()).isEmpty();
        assertThat(comparison.getDeltas()).containsEntry("routing.top1", new BigDecimal("-0.1"));
    }

    private EvalRunIdentity identity(
            String datasetVersion,
            String datasetHash,
            String bundleVersion,
            String bundleHash,
            String provider,
            String model,
            String judgeModel,
            String judgeRubricVersion) {
        return EvalRunIdentity.create(
                "deadbeef", datasetVersion, datasetHash, bundleVersion, bundleHash,
                "sha256:prompt", "sha256:retrieval", "BGE-small-zh-v1.5", "sha256:embedding",
                provider, model, "sha256:model-params", judgeModel, judgeRubricVersion);
    }
}
