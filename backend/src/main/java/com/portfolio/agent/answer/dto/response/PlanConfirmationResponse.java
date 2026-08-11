package com.portfolio.agent.answer.dto.response;

import java.util.Objects;
import java.util.List;

/** Opaque protocol fields required for a later confirm action, never for display or diagnostics. */
public final class PlanConfirmationResponse {

    private final String confirmationId;
    private final String expiresAt;
    private final String confirmationPlan;
    private final String planFingerprint;
    private final String integrityToken;
    private final List<String> triggerCodes;
    private final PendingPlanReferenceResponse pendingPlanReference;

    public PlanConfirmationResponse(
            String confirmationId,
            String expiresAt,
            String confirmationPlan,
            String planFingerprint,
            String integrityToken,
            List<String> triggerCodes,
            PendingPlanReferenceResponse pendingPlanReference) {
        this.confirmationId = requireText(confirmationId, "confirmationId");
        this.expiresAt = requireText(expiresAt, "expiresAt");
        this.confirmationPlan = requireText(confirmationPlan, "confirmationPlan");
        this.planFingerprint = requireText(planFingerprint, "planFingerprint");
        this.integrityToken = requireText(integrityToken, "integrityToken");
        this.triggerCodes = List.copyOf(Objects.requireNonNull(triggerCodes, "triggerCodes"));
        this.pendingPlanReference = Objects.requireNonNull(
                pendingPlanReference, "pendingPlanReference");
    }

    public String getConfirmationId() { return confirmationId; }
    public String getExpiresAt() { return expiresAt; }
    public String getConfirmationPlan() { return confirmationPlan; }
    public String getPlanFingerprint() { return planFingerprint; }
    public String getIntegrityToken() { return integrityToken; }
    public List<String> getTriggerCodes() { return triggerCodes; }
    public PendingPlanReferenceResponse getPendingPlanReference() { return pendingPlanReference; }

    @Override
    public String toString() {
        return "PlanConfirmationResponse{hasOpaqueEnvelope=true, hasIntegrityToken=true}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
