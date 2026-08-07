package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalVerdict;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EvalVerdictPolicy {

    private static final Set<String> PRIORITY_METRICS = Set.of(
            "routing.top1",
            "retrieval.hitAt5",
            "safety.boundaryPassRate");

    public EvalVerdict decide(
            EvalRunMode mode,
            EvalMetrics metrics,
            EvalComparison comparison,
            List<EvalGateResult> gates,
            EvalProviderAuthorization authorization) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(authorization, "authorization");

        for (EvalGateResult gate : gates) {
            if (!gate.isPassed() && gate.getSeverity() == EvalSeverity.BLOCKING) {
                return EvalVerdict.FAIL;
            }
        }

        if (comparison.isComparable()) {
            BigDecimal priorityLimit = metrics.getValue("policy.priorityRegressionLimit")
                    .getValue();
            BigDecimal globalLimit = metrics.getValue("policy.globalRegressionLimit")
                    .getValue();
            for (String metricName : comparison.getDeltas().keySet()) {
                BigDecimal delta = comparison.getDeltas().get(metricName);
                if (PRIORITY_METRICS.contains(metricName)) {
                    if (delta.compareTo(priorityLimit.negate()) < 0) {
                        return EvalVerdict.FAIL;
                    }
                } else if (delta.compareTo(globalLimit.negate()) < 0) {
                    return EvalVerdict.FAIL;
                }
            }
        }

        if (mode == EvalRunMode.PROVIDER
                && authorization != EvalProviderAuthorization.REAL_AUTHORIZED) {
            return EvalVerdict.INCOMPLETE;
        }
        return EvalVerdict.PASS;
    }
}
