package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;

import java.math.BigDecimal;
import java.util.Objects;

public final class EvalGateResult {

    public enum EvalComparisonOperator {
        GE,
        LE,
        EXACT
    }

    private final String metricName;
    private final BigDecimal observed;
    private final BigDecimal threshold;
    private final EvalComparisonOperator comparison;
    private final boolean passed;
    private final EvalSeverity severity;
    private final EvalReasonCode reasonCode;

    public EvalGateResult(
            String metricName,
            BigDecimal observed,
            BigDecimal threshold,
            EvalComparisonOperator comparison,
            boolean passed,
            EvalSeverity severity,
            EvalReasonCode reasonCode) {
        this.metricName = Objects.requireNonNull(metricName, "metricName");
        this.observed = Objects.requireNonNull(observed, "observed");
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.comparison = Objects.requireNonNull(comparison, "comparison");
        this.passed = passed;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public String getMetricName() { return metricName; }
    public BigDecimal getObserved() { return observed; }
    public BigDecimal getThreshold() { return threshold; }
    public EvalComparisonOperator getComparison() { return comparison; }
    public boolean isPassed() { return passed; }
    public EvalSeverity getSeverity() { return severity; }
    public EvalReasonCode getReasonCode() { return reasonCode; }

    @Override
    public String toString() {
        return "EvalGateResult{" + metricName + "=" + observed
                + " " + comparison + " " + threshold
                + " passed=" + passed + " severity=" + severity
                + " reason=" + reasonCode + '}';
    }
}
