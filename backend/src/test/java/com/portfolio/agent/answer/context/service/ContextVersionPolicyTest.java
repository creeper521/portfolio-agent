package com.portfolio.agent.answer.context.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextVersionPolicyTest {
    @Test
    void latestPoliciesRevalidateButStrictSnapshotIsStaleAndCannotTouch() {
        ContextVersionDecision latest = ContextVersionDecision.evaluate(
                ContextVersionPolicy.LATEST_REVALIDATED, "v1", "v2");
        ContextVersionDecision snapshot = ContextVersionDecision.evaluate(
                ContextVersionPolicy.SNAPSHOT_SELECT_THEN_LATEST, "v1", "v2");
        ContextVersionDecision strict = ContextVersionDecision.evaluate(
                ContextVersionPolicy.SNAPSHOT_STRICT, "v1", "v2");
        assertThat(latest.getStatus()).isEqualTo(ContextVersionStatus.REVALIDATED);
        assertThat(snapshot.getStatus()).isEqualTo(ContextVersionStatus.REVALIDATED);
        assertThat(strict.getStatus()).isEqualTo(ContextVersionStatus.STALE);
        assertThat(strict.isTouchAllowed()).isFalse();
    }

    @Test
    void infrastructureAndSubjectFailuresRemainDistinctFromStale() {
        assertThat(ContextVersionDecision.storeUnavailable(ContextVersionPolicy.SNAPSHOT_STRICT).getStatus())
                .isEqualTo(ContextVersionStatus.STORE_UNAVAILABLE);
        assertThat(ContextVersionDecision.subjectUnavailable(ContextVersionPolicy.LATEST_REVALIDATED).getStatus())
                .isEqualTo(ContextVersionStatus.SUBJECT_UNAVAILABLE);
        assertThat(ContextVersionDecision.invalidHandle(ContextVersionPolicy.SNAPSHOT_STRICT).getStatus())
                .isEqualTo(ContextVersionStatus.INVALID_HANDLE);
    }
}
