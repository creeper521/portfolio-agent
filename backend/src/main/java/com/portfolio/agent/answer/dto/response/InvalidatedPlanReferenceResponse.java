package com.portfolio.agent.answer.dto.response;

/** Opaque reference retained solely for a subsequent REGENERATE_PLAN action. */
public final class InvalidatedPlanReferenceResponse {

    private final String planId;
    private final String planFingerprint;

    public InvalidatedPlanReferenceResponse(String planId, String planFingerprint) {
        this.planId = requireText(planId, "planId");
        this.planFingerprint = requireText(planFingerprint, "planFingerprint");
    }

    public String getPlanId() { return planId; }
    public String getPlanFingerprint() { return planFingerprint; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
