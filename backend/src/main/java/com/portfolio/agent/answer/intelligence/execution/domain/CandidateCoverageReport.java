package com.portfolio.agent.answer.intelligence.execution.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete per-subject/per-target coverage returned by one attempt. */
public final class CandidateCoverageReport {

    public enum CoverageStatus {
        MATCHED,
        EVALUATED_NO_QUALIFYING_MATCH,
        NOT_EVALUATED_BUDGET
    }

    private final Map<String, CoverageStatus> statusesByTarget;

    public CandidateCoverageReport(Map<String, CoverageStatus> statusesByTarget) {
        Objects.requireNonNull(statusesByTarget, "statusesByTarget");
        if (statusesByTarget.isEmpty()) {
            throw new IllegalArgumentException("coverage must contain at least one target");
        }
        Map<String, CoverageStatus> copied = new LinkedHashMap<>();
        for (Map.Entry<String, CoverageStatus> entry : statusesByTarget.entrySet()) {
            copied.put(requireText(entry.getKey(), "target"),
                    Objects.requireNonNull(entry.getValue(), "coverageStatus"));
        }
        this.statusesByTarget = Map.copyOf(copied);
    }

    public static CandidateCoverageReport of(List<String> targets, CoverageStatus status) {
        Objects.requireNonNull(targets, "targets");
        Map<String, CoverageStatus> values = new LinkedHashMap<>();
        for (String target : targets) {
            String normalized = requireText(target, "target");
            if (values.put(normalized, Objects.requireNonNull(status, "status")) != null) {
                throw new IllegalArgumentException("coverage targets must be distinct");
            }
        }
        return new CandidateCoverageReport(values);
    }

    public Map<String, CoverageStatus> getStatusesByTarget() {
        return statusesByTarget;
    }

    public CoverageStatus getStatus(String target) {
        CoverageStatus status = statusesByTarget.get(target);
        if (status == null) {
            throw new IllegalArgumentException("coverage target is not present");
        }
        return status;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CandidateCoverageReport that
                && statusesByTarget.equals(that.statusesByTarget);
    }

    @Override
    public int hashCode() {
        return statusesByTarget.hashCode();
    }

    @Override
    public String toString() {
        return "CandidateCoverageReport{targetCount=" + statusesByTarget.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
