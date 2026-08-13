package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Closed support assessment with per-criterion evidence coverage. */
public final class EvidenceSupportAssessment {
    public enum SupportStatus { SUFFICIENT, PARTIAL, INSUFFICIENT, NOT_APPLICABLE }

    private final SupportStatus status;
    private final Map<String, List<ValidatedEvidenceUnit>> unitsByCriterion;
    private final List<String> omittedLabels;

    public EvidenceSupportAssessment(
            SupportStatus status, Map<String, List<ValidatedEvidenceUnit>> unitsByCriterion,
            List<String> omittedLabels) {
        this.status = Objects.requireNonNull(status, "status");
        LinkedHashMap<String, List<ValidatedEvidenceUnit>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<ValidatedEvidenceUnit>> entry
                : Objects.requireNonNull(unitsByCriterion, "unitsByCriterion").entrySet()) {
            String criterion = requireText(entry.getKey(), "criterion");
            List<ValidatedEvidenceUnit> units = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "criterion units"));
            if (units.size() > 2) {
                throw new IllegalArgumentException("a criterion may use at most two evidence units");
            }
            copied.put(criterion, units);
        }
        this.unitsByCriterion = java.util.Collections.unmodifiableMap(copied);
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (String label : Objects.requireNonNull(omittedLabels, "omittedLabels")) {
            labels.add(requireText(label, "omittedLabel"));
        }
        this.omittedLabels = List.copyOf(new ArrayList<>(labels));
    }

    public SupportStatus getStatus() { return status; }
    public Map<String, List<ValidatedEvidenceUnit>> getUnitsByCriterion() { return unitsByCriterion; }
    public List<String> getOmittedLabels() { return omittedLabels; }

    public List<ValidatedEvidenceUnit> getSelectedUnits() {
        List<ValidatedEvidenceUnit> result = new ArrayList<>();
        for (List<ValidatedEvidenceUnit> units : unitsByCriterion.values()) result.addAll(units);
        return List.copyOf(result);
    }

    public boolean isSupported() {
        return status == SupportStatus.SUFFICIENT || status == SupportStatus.PARTIAL;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
