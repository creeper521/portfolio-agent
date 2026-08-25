package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 预设问题的证据要求：约束回答该问题时每条必答 Claim 需要的公开证据规模。
 *
 * <p>minimumApprovedEvidencePerRequiredClaim 是每条 requiredClaim 至少要有的
 * APPROVED 证据条数，构造时强制不小于 1；publicOnly 表示只允许引用公开
 * （APPROVED 且 rawContentPublic=false）证据，公开快照中 ACTIVE 预设必须为 true。
 */
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
