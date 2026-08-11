package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/** Safe, user-facing explanation of an invalidated plan. */
public final class PlanChangeResponse {

    private final String summary;
    private final List<String> changeLabels;
    private final InvalidatedPlanReferenceResponse invalidatedPlanReference;

    public PlanChangeResponse(
            String summary,
            List<String> changeLabels,
            InvalidatedPlanReferenceResponse invalidatedPlanReference) {
        this.summary = requireText(summary, "summary");
        this.changeLabels = List.copyOf(Objects.requireNonNull(changeLabels, "changeLabels"));
        this.invalidatedPlanReference = invalidatedPlanReference;
    }

    public String getSummary() { return summary; }
    public List<String> getChangeLabels() { return changeLabels; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public InvalidatedPlanReferenceResponse getInvalidatedPlanReference() {
        return invalidatedPlanReference;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
