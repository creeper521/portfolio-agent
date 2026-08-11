package com.portfolio.agent.answer.dto.response;

/** Public-safe opaque identity required to request an adjustment of a pending plan. */
public final class PendingPlanReferenceResponse {

    private final String planId;
    private final String planFingerprint;

    public PendingPlanReferenceResponse(String planId, String planFingerprint) {
        this.planId = requireText(planId, "planId");
        this.planFingerprint = requireText(planFingerprint, "planFingerprint");
    }

    public String getPlanId() { return planId; }
    public String getPlanFingerprint() { return planFingerprint; }

    @Override
    public String toString() {
        return "PendingPlanReferenceResponse{hasReference=true, hasFingerprint=true}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
