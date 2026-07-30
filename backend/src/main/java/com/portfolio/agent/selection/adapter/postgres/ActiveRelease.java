package com.portfolio.agent.selection.adapter.postgres;

import java.util.Objects;

public final class ActiveRelease {

    private final String releaseId;
    private final String releaseVersion;

    public ActiveRelease(String releaseId, String releaseVersion) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }
}
