package com.portfolio.agent.evaluation.reporting;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EvalMetrics {

    public static final class MetricValue {
        private final BigDecimal value;
        private final long numerator;
        private final long denominator;

        public MetricValue(BigDecimal value, long numerator, long denominator) {
            this.value = Objects.requireNonNull(value, "value");
            this.numerator = numerator;
            if (denominator < 0) {
                throw new IllegalArgumentException("denominator must not be negative");
            }
            this.denominator = denominator;
        }

        public BigDecimal getValue() { return value; }
        public long getNumerator() { return numerator; }
        public long getDenominator() { return denominator; }
    }

    private final Map<String, MetricValue> metrics;

    public EvalMetrics(Map<String, MetricValue> metrics) {
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(metrics, "metrics")));
    }

    public static EvalMetrics empty() {
        return new EvalMetrics(Map.of());
    }

    public MetricValue getValue(String name) {
        MetricValue value = metrics.get(name);
        return value == null ? new MetricValue(BigDecimal.ZERO, 0L, 0L) : value;
    }

    public Map<String, MetricValue> getAll() {
        return metrics;
    }
}
