package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ClaimEvidenceLink {

    private final String id;
    private final String claimId;
    private final String evidenceId;
    private final SupportType supportType;
    private final String scope;
    private final ReviewStatus reviewStatus;

    @JsonCreator
    public ClaimEvidenceLink(
            @JsonProperty("id") String id,
            @JsonProperty("claimId") String claimId,
            @JsonProperty("evidenceId") String evidenceId,
            @JsonProperty("supportType") SupportType supportType,
            @JsonProperty("scope") String scope,
            @JsonProperty("reviewStatus") ReviewStatus reviewStatus
    ) {
        this.id = id;
        this.claimId = claimId;
        this.evidenceId = evidenceId;
        this.supportType = supportType;
        this.scope = scope;
        this.reviewStatus = reviewStatus;
    }

    public String getId() { return id; }
    public String getClaimId() { return claimId; }
    public String getEvidenceId() { return evidenceId; }
    public SupportType getSupportType() { return supportType; }
    public String getScope() { return scope; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClaimEvidenceLink that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(claimId, that.claimId)
                && Objects.equals(evidenceId, that.evidenceId)
                && supportType == that.supportType
                && Objects.equals(scope, that.scope)
                && reviewStatus == that.reviewStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, claimId, evidenceId, supportType, scope, reviewStatus);
    }
}
