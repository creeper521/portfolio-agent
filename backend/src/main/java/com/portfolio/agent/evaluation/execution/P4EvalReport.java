package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.domain.P4EvaluationDimension;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Safe P4 report: enums, counts, booleans and verdicts only. */
public final class P4EvalReport {
    private final Map<P4EvaluationDimension, EvalVerdict> dimensions;
    private final Map<P4SafetyCheck, Long> passedCheckCounts;
    private final long sampleCount;

    public P4EvalReport(Map<P4EvaluationDimension, EvalVerdict> dimensions,
            Map<P4SafetyCheck, Long> passedCheckCounts, long sampleCount) {
        this.dimensions = enumCopy(dimensions, P4EvaluationDimension.class);
        this.passedCheckCounts = enumCopy(passedCheckCounts, P4SafetyCheck.class);
        if (sampleCount < 0) throw new IllegalArgumentException("sampleCount must be nonnegative");
        this.sampleCount = sampleCount;
    }

    public Map<P4EvaluationDimension, EvalVerdict> getDimensions() { return dimensions; }
    public Map<P4SafetyCheck, Long> getPassedCheckCounts() { return passedCheckCounts; }
    public long getSampleCount() { return sampleCount; }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> dimensionValues = new LinkedHashMap<>();
        dimensions.forEach((dimension, verdict) ->
                dimensionValues.put(dimension.name(), verdict.name()));
        Map<String, Long> checkValues = new LinkedHashMap<>();
        passedCheckCounts.forEach((check, count) -> checkValues.put(check.name(), count));
        result.put("dimensions", dimensionValues);
        result.put("passedCheckCounts", checkValues);
        result.put("sampleCount", sampleCount);
        return Map.copyOf(result);
    }

    private static <K extends Enum<K>, V> Map<K, V> enumCopy(
            Map<K, V> source, Class<K> keyType) {
        Objects.requireNonNull(source, "source");
        EnumMap<K, V> copy = new EnumMap<>(keyType);
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")));
        return Map.copyOf(copy);
    }
}
