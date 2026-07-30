package com.portfolio.agent.portfolio.service;

import java.util.Objects;

public final class PublicReleaseActivationResult {

    private final String releaseId;
    private final String releaseStatus;

    public PublicReleaseActivationResult(String releaseId, String releaseStatus) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseStatus = Objects.requireNonNull(releaseStatus, "releaseStatus");
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }
}
