package com.portfolio.agent.evaluation.reporting;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvalComparison {

    private final boolean comparable;
    private final Map<String, BigDecimal> deltas;
    private final List<String> addedCaseIds;
    private final List<String> removedCaseIds;
    private final String reasonCode;

    public EvalComparison(
            boolean comparable,
            Map<String, BigDecimal> deltas,
            List<String> addedCaseIds,
            List<String> removedCaseIds) {
        this.comparable = comparable;
        this.deltas = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(deltas, "deltas")));
        this.addedCaseIds = List.copyOf(Objects.requireNonNull(addedCaseIds, "addedCaseIds"));
        this.removedCaseIds = List.copyOf(Objects.requireNonNull(removedCaseIds, "removedCaseIds"));
        this.reasonCode = comparable ? "PASS" : "IDENTITY_NOT_COMPARABLE";
    }

    public static EvalComparison notComparable() {
        return new EvalComparison(false, Map.of(), List.of(), List.of());
    }

    public boolean isComparable() { return comparable; }
    public Map<String, BigDecimal> getDeltas() { return deltas; }
    public List<String> getAddedCaseIds() { return addedCaseIds; }
    public List<String> getRemovedCaseIds() { return removedCaseIds; }
    public String getReasonCode() { return reasonCode; }
}
