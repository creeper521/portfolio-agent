package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class QuestionEvidenceRequirement {

    private final int minimumApprovedEvidencePerRequiredClaim;
    private final boolean publicOnly;

    @JsonCreator
    public QuestionEvidenceRequirement(
            @JsonProperty("minimumApprovedEvidencePerRequiredClaim") int minimumApprovedEvidencePerRequiredClaim,
            @JsonProperty("publicOnly") boolean publicOnly
    ) {
        if (minimumApprovedEvidencePerRequiredClaim < 1) {
            throw new IllegalArgumentException(
                    "minimumApprovedEvidencePerRequiredClaim must be at least 1");
        }
        this.minimumApprovedEvidencePerRequiredClaim = minimumApprovedEvidencePerRequiredClaim;
        this.publicOnly = publicOnly;
    }

    public int getMinimumApprovedEvidencePerRequiredClaim() {
        return minimumApprovedEvidencePerRequiredClaim;
    }

    public boolean isPublicOnly() {
        return publicOnly;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionEvidenceRequirement that)) {
            return false;
        }
        return minimumApprovedEvidencePerRequiredClaim
                == that.minimumApprovedEvidencePerRequiredClaim
                && publicOnly == that.publicOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimumApprovedEvidencePerRequiredClaim, publicOnly);
    }
}
