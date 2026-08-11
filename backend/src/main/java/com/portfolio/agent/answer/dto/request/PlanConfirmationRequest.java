package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Opaque confirmation fields; callers must forward them without inspecting them. */
public final class PlanConfirmationRequest {

    @NotBlank(message = "confirmationId is required")
    @Size(max = 200, message = "confirmationId must not exceed 200 characters")
    private final String confirmationId;

    @NotBlank(message = "confirmationPlan is required")
    @Size(max = 30000, message = "confirmationPlan must not exceed 30000 characters")
    private final String confirmationPlan;

    @NotBlank(message = "planFingerprint is required")
    @Size(max = 200, message = "planFingerprint must not exceed 200 characters")
    private final String planFingerprint;

    @NotBlank(message = "integrityToken is required")
    @Size(max = 30000, message = "integrityToken must not exceed 30000 characters")
    private final String integrityToken;

    @JsonCreator
    public PlanConfirmationRequest(
            @JsonProperty("confirmationId") String confirmationId,
            @JsonProperty("confirmationPlan") String confirmationPlan,
            @JsonProperty("planFingerprint") String planFingerprint,
            @JsonProperty("integrityToken") String integrityToken) {
        this.confirmationId = confirmationId;
        this.confirmationPlan = confirmationPlan;
        this.planFingerprint = planFingerprint;
        this.integrityToken = integrityToken;
    }

    public String getConfirmationId() { return confirmationId; }
    public String getConfirmationPlan() { return confirmationPlan; }
    public String getPlanFingerprint() { return planFingerprint; }
    public String getIntegrityToken() { return integrityToken; }

    @Override
    public String toString() {
        return "PlanConfirmationRequest{hasOpaqueEnvelope=" + hasText(confirmationPlan)
                + ", hasIntegrityToken=" + hasText(integrityToken) + '}';
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
