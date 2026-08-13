package com.portfolio.agent.answer.context.domain;

import java.util.List;
import java.util.Objects;

/** Safe UI projection; it deliberately contains no handles, versions, or content. */
public final class ConversationContextSummary {
    private final String schemaVersion;
    private final ConversationContinuationStatus status;
    private final String recentTaskType;
    private final List<String> subjectLabels;
    private final List<String> facetLabels;
    private final List<String> comparisonDimensionLabels;
    private final List<String> preferenceLabels;
    private final boolean canRefine;

    public ConversationContextSummary(
            String schemaVersion,
            ConversationContinuationStatus status,
            String recentTaskType,
            List<String> subjectLabels,
            List<String> facetLabels,
            List<String> comparisonDimensionLabels,
            List<String> preferenceLabels,
            boolean canRefine) {
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        this.status = Objects.requireNonNull(status, "status");
        this.recentTaskType = optionalText(recentTaskType);
        this.subjectLabels = copy(subjectLabels, "subjectLabels");
        this.facetLabels = copy(facetLabels, "facetLabels");
        this.comparisonDimensionLabels = copy(
                comparisonDimensionLabels, "comparisonDimensionLabels");
        this.preferenceLabels = copy(preferenceLabels, "preferenceLabels");
        this.canRefine = canRefine;
    }

    public String getSchemaVersion() { return schemaVersion; }
    public ConversationContinuationStatus getStatus() { return status; }
    public String getRecentTaskType() { return recentTaskType; }
    public List<String> getSubjectLabels() { return subjectLabels; }
    public List<String> getFacetLabels() { return facetLabels; }
    public List<String> getComparisonDimensionLabels() { return comparisonDimensionLabels; }
    public List<String> getPreferenceLabels() { return preferenceLabels; }
    public boolean isCanRefine() { return canRefine; }

    @Override
    public String toString() {
        return "ConversationContextSummary{schemaVersion=" + schemaVersion
                + ", status=" + status + ", recentTaskType=" + recentTaskType
                + ", subjectLabelCount=" + subjectLabels.size() + '}';
    }

    private static List<String> copy(List<String> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name));
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String name) {
        String normalized = optionalText(value);
        if (normalized == null) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
