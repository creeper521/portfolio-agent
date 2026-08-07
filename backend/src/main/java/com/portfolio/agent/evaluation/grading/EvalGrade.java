package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalSeverity;

import java.util.Objects;

public final class EvalGrade {

    private final String caseId;
    private final EvalLayer layer;
    private final int trialIndex;
    private final String graderType;
    private final EvalSeverity severity;
    private final boolean passed;
    private final EvalReasonCode reasonCode;
    private final long numerator;
    private final long denominator;

    public EvalGrade(
            String caseId,
            EvalLayer layer,
            int trialIndex,
            String graderType,
            EvalSeverity severity,
            boolean passed,
            EvalReasonCode reasonCode,
            long numerator,
            long denominator) {
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.layer = Objects.requireNonNull(layer, "layer");
        if (trialIndex < 1) {
            throw new IllegalArgumentException("trialIndex must be at least 1");
        }
        this.trialIndex = trialIndex;
        this.graderType = Objects.requireNonNull(graderType, "graderType");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.passed = passed;
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        if (denominator < 1) {
            throw new IllegalArgumentException("denominator must be at least 1");
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public String getCaseId() { return caseId; }
    public EvalLayer getLayer() { return layer; }
    public int getTrialIndex() { return trialIndex; }
    public String getGraderType() { return graderType; }
    public EvalSeverity getSeverity() { return severity; }
    public boolean isPassed() { return passed; }
    public EvalReasonCode getReasonCode() { return reasonCode; }
    public long getNumerator() { return numerator; }
    public long getDenominator() { return denominator; }
}
