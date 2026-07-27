package com.portfolio.agent.release.benchmark;

import java.util.Objects;

public final class RetrievalExpectedRank {

    private final String targetType;
    private final String targetId;
    private final Integer rank;

    public RetrievalExpectedRank(String targetType, String targetId, Integer rank) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.rank = rank;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public Integer getRank() {
        return rank;
    }
}
