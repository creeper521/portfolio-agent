package com.portfolio.agent.portfolio.dto.response;

import java.util.Objects;

/**
 * Public deployment capability projection. It exposes only closed readiness
 * values and never provider, state-store or credential details.
 */
public final class AgentAvailabilityResponse {
    public enum Status { AVAILABLE, UNAVAILABLE }
    public enum FreeTextSemanticRouting { AVAILABLE, DISABLED }

    private final Status status;
    private final FreeTextSemanticRouting freeTextSemanticRouting;

    public AgentAvailabilityResponse(
            Status status,
            FreeTextSemanticRouting freeTextSemanticRouting) {
        this.status = Objects.requireNonNull(status, "status");
        this.freeTextSemanticRouting = Objects.requireNonNull(
                freeTextSemanticRouting, "freeTextSemanticRouting");
        if (status == Status.UNAVAILABLE
                && freeTextSemanticRouting
                != FreeTextSemanticRouting.DISABLED) {
            throw new IllegalArgumentException(
                    "unavailable Agent cannot expose free text routing");
        }
    }

    public Status getStatus() {
        return status;
    }

    public FreeTextSemanticRouting getFreeTextSemanticRouting() {
        return freeTextSemanticRouting;
    }

    public static AgentAvailabilityResponse available() {
        return available(FreeTextSemanticRouting.AVAILABLE);
    }

    public static AgentAvailabilityResponse available(
            FreeTextSemanticRouting freeTextSemanticRouting) {
        return new AgentAvailabilityResponse(
                Status.AVAILABLE, freeTextSemanticRouting);
    }

    public static AgentAvailabilityResponse unavailable() {
        return new AgentAvailabilityResponse(
                Status.UNAVAILABLE, FreeTextSemanticRouting.DISABLED);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AgentAvailabilityResponse that
                && status == that.status
                && freeTextSemanticRouting
                == that.freeTextSemanticRouting;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, freeTextSemanticRouting);
    }
}
