package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.grading.EvalGrade;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EvalRunReport {

    private final String runId;
    private final EvalRunMode mode;
    private final EvalRunIdentity identity;
    private final EvalVerdict verdict;
    private final EvalMetrics metrics;
    private final EvalComparison comparison;
    private final List<EvalGateResult> gates;
    private final List<EvalObservation> observations;
    private final List<EvalGrade> grades;
    private final Optional<String> baselineId;
private final com.portfolio.agent.evaluation.domain.EvalProviderAuthorization providerAuthorization;

    public EvalRunReport(
            String runId,
            EvalRunMode mode,
            EvalRunIdentity identity,
            EvalVerdict verdict,
            EvalMetrics metrics,
            EvalComparison comparison,
            List<EvalGateResult> gates,
            List<EvalObservation> observations,
            List<EvalGrade> grades,
            Optional<String> baselineId,
            com.portfolio.agent.evaluation.domain.EvalProviderAuthorization
                    providerAuthorization) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.comparison = Objects.requireNonNull(comparison, "comparison");
        this.gates = List.copyOf(Objects.requireNonNull(gates, "gates"));
        this.observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        this.grades = List.copyOf(Objects.requireNonNull(grades, "grades"));
        this.baselineId = Objects.requireNonNull(baselineId, "baselineId");
        this.providerAuthorization = Objects.requireNonNull(
                providerAuthorization, "providerAuthorization");
    }

    public String getRunId() { return runId; }
    public EvalRunMode getMode() { return mode; }
    public EvalRunIdentity getIdentity() { return identity; }
    public EvalVerdict getVerdict() { return verdict; }
    public EvalMetrics getMetrics() { return metrics; }
    public EvalComparison getComparison() { return comparison; }
    public List<EvalGateResult> getGates() { return gates; }
    public List<EvalObservation> getObservations() { return observations; }
    public List<EvalGrade> getGrades() { return grades; }
    public Optional<String> getBaselineId() { return baselineId; }
    public com.portfolio.agent.evaluation.domain.EvalProviderAuthorization
            getProviderAuthorization() { return providerAuthorization; }
}
