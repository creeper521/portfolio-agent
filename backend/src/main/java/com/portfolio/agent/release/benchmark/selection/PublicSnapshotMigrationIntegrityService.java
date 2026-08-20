package com.portfolio.agent.release.benchmark.selection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.util.Objects;

/**
 * Real migration-integrity path: both file and PostgreSQL repositories provide
 * complete public runtime snapshots, which are fingerprinted internally.
 */
public final class PublicSnapshotMigrationIntegrityService {
    private final PublicSnapshotFingerprintFactory fingerprintFactory;
    private final MigrationIntegrityEvaluator evaluator;

    public PublicSnapshotMigrationIntegrityService(ObjectMapper objectMapper) {
        this(new PublicSnapshotFingerprintFactory(objectMapper), new MigrationIntegrityEvaluator());
    }

    public PublicSnapshotMigrationIntegrityService(
            PublicSnapshotFingerprintFactory fingerprintFactory,
            MigrationIntegrityEvaluator evaluator) {
        this.fingerprintFactory = Objects.requireNonNull(fingerprintFactory, "fingerprintFactory");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public MigrationIntegrityResult compare(
            RuntimeContentSnapshot fileSnapshot,
            RuntimeContentSnapshot postgresSnapshot) {
        return evaluator.compare(
                fingerprintFactory.create(Objects.requireNonNull(fileSnapshot, "fileSnapshot")),
                fingerprintFactory.create(Objects.requireNonNull(postgresSnapshot, "postgresSnapshot")));
    }
}
