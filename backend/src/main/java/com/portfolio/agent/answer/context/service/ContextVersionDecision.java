package com.portfolio.agent.answer.context.service;

import java.util.Objects;

public final class ContextVersionDecision {
    private final ContextVersionStatus status;
    private final ContextVersionPolicy policy;
    private final String expectedVersion;
    private final String actualVersion;
    private final boolean touchAllowed;

    private ContextVersionDecision(ContextVersionStatus status, ContextVersionPolicy policy,
                                   String expectedVersion, String actualVersion, boolean touchAllowed) {
        this.status = Objects.requireNonNull(status, "status"); this.policy = Objects.requireNonNull(policy, "policy");
        this.expectedVersion = expectedVersion; this.actualVersion = actualVersion; this.touchAllowed = touchAllowed;
    }
    public static ContextVersionDecision evaluate(ContextVersionPolicy policy, String expectedVersion, String actualVersion) {
        Objects.requireNonNull(policy, "policy");
        if (expectedVersion == null || expectedVersion.isBlank()) return new ContextVersionDecision(ContextVersionStatus.CURRENT, policy, null, actualVersion, true);
        if (actualVersion == null || actualVersion.isBlank()) return new ContextVersionDecision(ContextVersionStatus.SOURCE_CHANGED, policy, expectedVersion, actualVersion, false);
        if (expectedVersion.equals(actualVersion)) return new ContextVersionDecision(ContextVersionStatus.CURRENT, policy, expectedVersion, actualVersion, true);
        return switch (policy) {
            case LATEST_REVALIDATED -> new ContextVersionDecision(ContextVersionStatus.REVALIDATED, policy, expectedVersion, actualVersion, true);
            case SNAPSHOT_SELECT_THEN_LATEST -> new ContextVersionDecision(ContextVersionStatus.REVALIDATED, policy, expectedVersion, actualVersion, true);
            case SNAPSHOT_STRICT -> new ContextVersionDecision(ContextVersionStatus.STALE, policy, expectedVersion, actualVersion, false);
        };
    }
    public static ContextVersionDecision invalidHandle(ContextVersionPolicy policy) { return new ContextVersionDecision(ContextVersionStatus.INVALID_HANDLE, policy, null, null, false); }
    public static ContextVersionDecision storeUnavailable(ContextVersionPolicy policy) { return new ContextVersionDecision(ContextVersionStatus.STORE_UNAVAILABLE, policy, null, null, false); }
    public static ContextVersionDecision subjectUnavailable(ContextVersionPolicy policy) { return new ContextVersionDecision(ContextVersionStatus.SUBJECT_UNAVAILABLE, policy, null, null, false); }
    public ContextVersionStatus getStatus() { return status; }
    public ContextVersionPolicy getPolicy() { return policy; }
    public String getExpectedVersion() { return expectedVersion; }
    public String getActualVersion() { return actualVersion; }
    public boolean isTouchAllowed() { return touchAllowed; }
    public boolean isStale() { return status == ContextVersionStatus.STALE; }
}
