package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalVerdictPolicyTest {

    private final EvalVerdictPolicy policy = new EvalVerdictPolicy();

    @Test
    void oneHardErrorFailsTheRun() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparableComparison(),
                List.of(gate("hardError.count", "0", "0",
                        EvalGateResult.EvalComparisonOperator.LE, false, EvalSeverity.BLOCKING,
                        EvalReasonCode.EXECUTOR_ERROR)),
                EvalProviderAuthorization.NOT_AUTHORIZED);

        assertThat(verdict).isEqualTo(EvalVerdict.FAIL);
    }

    @Test
    void priorityRegressionBeyondTwoPercentFailsButExactlyTwoPercentDoesNot() {
        EvalVerdict failing = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparisonWithDelta("routing.top1", "-0.0201"),
                List.of(),
                EvalProviderAuthorization.NOT_AUTHORIZED);
        assertThat(failing).isEqualTo(EvalVerdict.FAIL);

        EvalVerdict passing = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparisonWithDelta("routing.top1", "-0.02"),
                List.of(),
                EvalProviderAuthorization.NOT_AUTHORIZED);
        assertThat(passing).isEqualTo(EvalVerdict.PASS);
    }

    @Test
    void globalRegressionBeyondThreePercentFails() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparisonWithDelta("structure.answerQualityPassRate", "-0.0301"),
                List.of(),
                EvalProviderAuthorization.NOT_AUTHORIZED);

        assertThat(verdict).isEqualTo(EvalVerdict.FAIL);
    }

    @Test
    void p95LatencyOverBudgetFails() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparableComparison(),
                List.of(gate("provider.p95LatencyMs", "25000", "20000",
                        EvalGateResult.EvalComparisonOperator.LE, false, EvalSeverity.BLOCKING,
                        EvalReasonCode.FALSE_SUFFICIENT)),
                EvalProviderAuthorization.NOT_AUTHORIZED);

        assertThat(verdict).isEqualTo(EvalVerdict.FAIL);
    }

    @Test
    void offlinePassingWithoutRealProviderRunIsIncompleteInProviderMode() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.PROVIDER,
                emptyMetrics(),
                comparableComparison(),
                List.of(),
                EvalProviderAuthorization.MOCK_ONLY);

        assertThat(verdict).isEqualTo(EvalVerdict.INCOMPLETE);
    }

    @Test
    void providerHardErrorFails() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.PROVIDER,
                emptyMetrics(),
                comparableComparison(),
                List.of(gate("provider.trialPassRate", "0.5", "0.9",
                        EvalGateResult.EvalComparisonOperator.GE, false, EvalSeverity.BLOCKING,
                        EvalReasonCode.FALSE_SUFFICIENT)),
                EvalProviderAuthorization.REAL_AUTHORIZED);

        assertThat(verdict).isEqualTo(EvalVerdict.FAIL);
    }

    @Test
    void allNecessaryGatesPassingYieldsPass() {
        EvalVerdict verdict = policy.decide(
                EvalRunMode.OFFLINE,
                emptyMetrics(),
                comparableComparison(),
                List.of(gate("routing.top1", "1.0", "0.9",
                        EvalGateResult.EvalComparisonOperator.GE, true, EvalSeverity.BLOCKING,
                        EvalReasonCode.PASS)),
                EvalProviderAuthorization.NOT_AUTHORIZED);

        assertThat(verdict).isEqualTo(EvalVerdict.PASS);
    }

    private EvalMetrics emptyMetrics() {
        return new EvalMetrics(Map.of(
                "policy.priorityRegressionLimit",
                new EvalMetrics.MetricValue(new BigDecimal("0.02"), 2L, 100L),
                "policy.globalRegressionLimit",
                new EvalMetrics.MetricValue(new BigDecimal("0.03"), 3L, 100L)));
    }

    private EvalComparison comparableComparison() {
        return new EvalComparison(true, Map.of(), List.of(), List.of());
    }

    private EvalComparison comparisonWithDelta(String name, String delta) {
        return new EvalComparison(true, Map.of(name, new BigDecimal(delta)),
                List.of(), List.of());
    }

    private EvalGateResult gate(
            String metricName,
            String observed,
            String threshold,
            EvalGateResult.EvalComparisonOperator comparison,
            boolean passed,
            EvalSeverity severity,
            EvalReasonCode reasonCode) {
        return new EvalGateResult(metricName, new BigDecimal(observed),
                new BigDecimal(threshold), comparison, passed, severity, reasonCode);
    }
}
