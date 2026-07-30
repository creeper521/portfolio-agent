package com.portfolio.agent.portfolio.repository.postgres;

interface PublicRuntimeSnapshotStore {

    PublicReleaseMetadata findActiveRelease();

    StoredRuntimeSnapshot findRuntimeSnapshot(String releaseId);
}
