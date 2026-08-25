package com.portfolio.agent.portfolio.service;

import java.util.Objects;

/**
 * 公开发布激活操作的结果载体。
 *
 * <p>仅携带被激活发布的 releaseId 与激活后的发布状态（如 PUBLISHED），
 * 由 {@link PublicReleaseActivationService} 返回给治理流程使用。
 */
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
