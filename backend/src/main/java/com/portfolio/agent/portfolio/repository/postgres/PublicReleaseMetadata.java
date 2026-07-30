package com.portfolio.agent.portfolio.repository.postgres;

import java.util.Objects;

final class PublicReleaseMetadata {

    private final String releaseId;
    private final String releaseVersion;
    private final String schemaVersion;
    private final String contentHash;
    private final String status;

    PublicReleaseMetadata(
            String releaseId,
            String releaseVersion,
            String schemaVersion,
            String contentHash,
            String status) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.contentHash = contentHash;
        this.status = Objects.requireNonNull(status, "status");
    }

    String getReleaseId() {
        return releaseId;
    }

    String getReleaseVersion() {
        return releaseVersion;
    }

    String getSchemaVersion() {
        return schemaVersion;
    }

    String getContentHash() {
        return contentHash;
    }

    String getStatus() {
        return status;
    }
}
