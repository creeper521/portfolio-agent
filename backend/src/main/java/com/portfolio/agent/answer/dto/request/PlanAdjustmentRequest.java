package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bounded natural-language adjustment bound to an opaque pending-plan identity. */
public final class PlanAdjustmentRequest {

    @NotBlank(message = "planAdjustment.instruction is required")
    @Size(max = 500, message = "planAdjustment.instruction must not exceed 500 characters")
    private final String instruction;

    @Valid
    @NotNull(message = "planAdjustment.pendingPlanReference is required")
    private final SemanticContextRequest.PendingPlanReferenceRequest pendingPlanReference;

    @JsonCreator
    public PlanAdjustmentRequest(
            @JsonProperty("instruction") String instruction,
            @JsonProperty("pendingPlanReference")
            SemanticContextRequest.PendingPlanReferenceRequest pendingPlanReference) {
        this.instruction = instruction;
        this.pendingPlanReference = pendingPlanReference;
    }

    public String getInstruction() { return instruction; }

    public SemanticContextRequest.PendingPlanReferenceRequest getPendingPlanReference() {
        return pendingPlanReference;
    }

    @Override
    public String toString() {
        return "PlanAdjustmentRequest{instruction=<redacted>, hasPendingPlanReference="
                + (pendingPlanReference != null) + '}';
    }
}
