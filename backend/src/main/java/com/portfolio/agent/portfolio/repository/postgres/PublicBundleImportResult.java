package com.portfolio.agent.portfolio.repository.postgres;

import java.util.Objects;

public final class PublicBundleImportResult {

    private final String releaseId;
    private final String releaseVersion;
    private final String releaseStatus;

    public PublicBundleImportResult(String releaseId, String releaseVersion, String releaseStatus) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.releaseStatus = Objects.requireNonNull(releaseStatus, "releaseStatus");
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }
}
