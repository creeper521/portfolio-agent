package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.Objects;

/** 当前生效的公开内容发布（不可变值对象）：检索必须锁定在该 releaseId 对应的快照上。 */
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
