package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalRunIdentity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvalBaseline {

    private final String baselineId;
    private final EvalRunIdentity identity;
    private final Map<String, BigDecimal> metrics;
    private final List<String> baselineCaseIds;

    public EvalBaseline(
            String baselineId,
            EvalRunIdentity identity,
            Map<String, BigDecimal> metrics) {
        this(baselineId, identity, metrics, List.of());
    }

    public EvalBaseline(
            String baselineId,
            EvalRunIdentity identity,
            Map<String, BigDecimal> metrics,
            List<String> baselineCaseIds) {
        this.baselineId = Objects.requireNonNull(baselineId, "baselineId");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.metrics = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(metrics, "metrics")));
        this.baselineCaseIds = List.copyOf(
                Objects.requireNonNull(baselineCaseIds, "baselineCaseIds"));
    }

    public String getBaselineId() {
        return baselineId;
    }

    public EvalRunIdentity getIdentity() {
        return identity;
    }

    public Map<String, BigDecimal> getMetrics() {
        return metrics;
    }

    public List<String> getBaselineCaseIds() {
        return baselineCaseIds;
    }
}
