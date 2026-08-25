package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Objects;

/**
 * Session-row pointer; status is always derived from its expiry.
 *
 * <p>活跃讨论指针：会话行中记录当前锁定讨论的上下文句柄、项目与过期
 * 时间。活跃/过期状态永远由过期时间推导（{@link #statusAt}），不单独
 * 存储，避免状态与时间漂移。</p>
 */
public final class ActiveDiscussionPointer {
    private final String contextHandle;
    private final String projectId;
    private final Instant contextExpiresAt;

    public ActiveDiscussionPointer(
            String contextHandle,
            String projectId,
            Instant contextExpiresAt) {
        this.contextHandle = ContinuationContext.text(
                contextHandle, "contextHandle");
        this.projectId = ContinuationContext.text(projectId, "projectId");
        this.contextExpiresAt = Objects.requireNonNull(
                contextExpiresAt, "contextExpiresAt");
    }

    public String getContextHandle() { return contextHandle; }
    public String getProjectId() { return projectId; }
    public Instant getContextExpiresAt() { return contextExpiresAt; }

    /** 按给定时间推导当前状态。 */
    public Status statusAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(contextExpiresAt)
                ? Status.ACTIVE : Status.EXPIRED;
    }

    /** 判断句柄是否等于期望代（用于讨论变更的乐观并发守卫）。 */
    public boolean matchesGeneration(String expectedHandle) {
        return contextHandle.equals(expectedHandle);
    }

    /** 指针状态：活跃/已过期（由过期时间推导）。 */
    public enum Status {
        ACTIVE,
        EXPIRED
    }
}
