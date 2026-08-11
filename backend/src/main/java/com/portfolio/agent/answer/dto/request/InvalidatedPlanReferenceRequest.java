package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reference supplied only when a user asks to regenerate an invalidated plan. */
public final class InvalidatedPlanReferenceRequest {

    @NotBlank(message = "planId is required")
    @Size(max = 200, message = "planId must not exceed 200 characters")
    private final String planId;

    @NotBlank(message = "planFingerprint is required")
    @Size(max = 200, message = "planFingerprint must not exceed 200 characters")
    private final String planFingerprint;

    @JsonCreator
    public InvalidatedPlanReferenceRequest(
            @JsonProperty("planId") String planId,
            @JsonProperty("planFingerprint") String planFingerprint) {
        this.planId = planId;
        this.planFingerprint = planFingerprint;
    }

    public String getPlanId() { return planId; }
    public String getPlanFingerprint() { return planFingerprint; }

    @Override
    public String toString() {
        return "InvalidatedPlanReferenceRequest{hasReference=" + hasText(planId)
                + ", hasFingerprint=" + hasText(planFingerprint) + '}';
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
