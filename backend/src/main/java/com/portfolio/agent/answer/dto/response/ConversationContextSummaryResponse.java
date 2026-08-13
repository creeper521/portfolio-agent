package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.context.domain.ConversationContextSummary;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;

import java.util.List;

/** Safe context summary response; no handle, version, question, answer or model text. */
public final class ConversationContextSummaryResponse {
    public static final String CONTRACT_VERSION = "p3-context-summary-v1";
    private final String contractVersion;
    private final ConversationContinuationStatus continuationStatus;
    private final Summary summary;

    public ConversationContextSummaryResponse(
            ConversationContinuationStatus continuationStatus,
            Summary summary) {
        this.contractVersion = CONTRACT_VERSION;
        this.continuationStatus = java.util.Objects.requireNonNull(
                continuationStatus, "continuationStatus");
        this.summary = summary;
    }

    public static ConversationContextSummaryResponse available(
            ConversationContextSummary value) {
        return new ConversationContextSummaryResponse(
                value.getStatus(), Summary.from(value));
    }

    public static ConversationContextSummaryResponse unavailable(
            ConversationContinuationStatus status) {
        if (status == ConversationContinuationStatus.AVAILABLE) {
            throw new IllegalArgumentException("available status requires a summary");
        }
        return new ConversationContextSummaryResponse(status, null);
    }

    public String getContractVersion() { return contractVersion; }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Summary getSummary() { return summary; }

    public static final class Summary {
        private final String recentTaskType;
        private final List<String> subjectLabels;
        private final List<String> facetLabels;
        private final List<String> comparisonDimensionLabels;
        private final List<String> preferenceLabels;
        private final boolean canRefine;

        private Summary(String recentTaskType, List<String> subjectLabels,
                List<String> facetLabels, List<String> comparisonDimensionLabels,
                List<String> preferenceLabels, boolean canRefine) {
            this.recentTaskType = recentTaskType;
            this.subjectLabels = List.copyOf(subjectLabels);
            this.facetLabels = List.copyOf(facetLabels);
            this.comparisonDimensionLabels = List.copyOf(comparisonDimensionLabels);
            this.preferenceLabels = List.copyOf(preferenceLabels);
            this.canRefine = canRefine;
        }

        private static Summary from(ConversationContextSummary value) {
            return new Summary(value.getRecentTaskType(), value.getSubjectLabels(),
                    value.getFacetLabels(), value.getComparisonDimensionLabels(),
                    value.getPreferenceLabels(), value.isCanRefine());
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getRecentTaskType() { return recentTaskType; }
        public List<String> getSubjectLabels() { return subjectLabels; }
        public List<String> getFacetLabels() { return facetLabels; }
        public List<String> getComparisonDimensionLabels() { return comparisonDimensionLabels; }
        public List<String> getPreferenceLabels() { return preferenceLabels; }
        public boolean isCanRefine() { return canRefine; }
    }
}
