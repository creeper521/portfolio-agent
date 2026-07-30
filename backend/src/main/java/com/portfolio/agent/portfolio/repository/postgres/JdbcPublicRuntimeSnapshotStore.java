package com.portfolio.agent.portfolio.repository.postgres;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcPublicRuntimeSnapshotStore implements PublicRuntimeSnapshotStore {

    private static final String ACTIVE_RELEASE_SQL = """
            SELECT cr.release_id::text, cr.release_version, cr.schema_version, cr.content_hash, cr.status
            FROM active_release ar
            JOIN content_release cr ON cr.release_id = ar.release_id
            WHERE ar.singleton = true
            """;
    private static final String SNAPSHOT_SQL = """
            SELECT payload::text, payload_checksum
            FROM release_runtime_snapshot
            WHERE release_id = CAST(? AS uuid)
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcPublicRuntimeSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PublicReleaseMetadata findActiveRelease() {
        List<PublicReleaseMetadata> releases = jdbcTemplate.query(
                ACTIVE_RELEASE_SQL,
                (resultSet, rowNumber) -> new PublicReleaseMetadata(
                        resultSet.getString("release_id"),
                        resultSet.getString("release_version"),
                        resultSet.getString("schema_version"),
                        resultSet.getString("content_hash"),
                        resultSet.getString("status")));
        if (releases.size() > 1) {
            throw new IllegalStateException("more than one active public release");
        }
        return releases.isEmpty() ? null : releases.getFirst();
    }

    @Override
    public StoredRuntimeSnapshot findRuntimeSnapshot(String releaseId) {
        List<StoredRuntimeSnapshot> snapshots = jdbcTemplate.query(
                SNAPSHOT_SQL,
                (resultSet, rowNumber) -> new StoredRuntimeSnapshot(
                        resultSet.getString("payload"),
                        resultSet.getString("payload_checksum")),
                releaseId);
        if (snapshots.size() > 1) {
            throw new IllegalStateException("more than one runtime snapshot for public release");
        }
        return snapshots.isEmpty() ? null : snapshots.getFirst();
    }
}
