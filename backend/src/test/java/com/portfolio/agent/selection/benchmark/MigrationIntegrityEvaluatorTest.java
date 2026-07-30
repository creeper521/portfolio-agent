package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationIntegrityEvaluatorTest {
    @Test
    void exactPublicSemanticMatchScoresOne() {
        PublicSnapshotFingerprint fingerprint = fingerprint("hash-a", "hash-b");
        MigrationIntegrityResult result = new MigrationIntegrityEvaluator().compare(fingerprint, fingerprint);
        assertThat(result.isCompleteMatch()).isTrue();
        assertThat(result.getScore()).isEqualTo(1.0);
        assertThat(result.getMismatches()).isEmpty();
    }

    @Test
    void anyCountRelationOrHashMismatchPreventsPerfectScore() {
        PublicSnapshotFingerprint file = fingerprint("hash-a", "hash-b");
        PublicSnapshotFingerprint database = new PublicSnapshotFingerprint(
                "4.0", "2026-07-29.1", Map.of("PROJECT", 5, "CASE", 48),
                Set.of("claim-a:evidence-a"), Map.of("portfolio", "changed", "retrieval", "hash-b"));
        MigrationIntegrityResult result = new MigrationIntegrityEvaluator().compare(file, database);
        assertThat(result.isCompleteMatch()).isFalse();
        assertThat(result.getScore()).isLessThan(1.0);
        assertThat(result.getMismatches()).contains("count:CASE", "hash:portfolio");
    }

    @Test
    void relationshipMismatchAlonePreventsPerfectScore() {
        PublicSnapshotFingerprint file = fingerprint("hash-a", "hash-b");
        PublicSnapshotFingerprint database = new PublicSnapshotFingerprint(
                "4.0", "2026-07-29.1", Map.of("PROJECT", 5, "CASE", 49),
                Set.of("claim-a:evidence-other"),
                Map.of("portfolio", "hash-a", "retrieval", "hash-b"));

        MigrationIntegrityResult result = new MigrationIntegrityEvaluator().compare(file, database);

        assertThat(result.getMismatches()).containsExactly("relationships");
        assertThat(result.getScore()).isLessThan(1.0);
    }

    @Test
    void emptyFingerprintCannotClaimPerfectMigration() {
        assertThatThrownBy(() -> new PublicSnapshotFingerprint(
                "4.0", "2026-07-29.1", Map.of(), Set.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PublicSnapshotFingerprint fingerprint(String portfolioHash, String retrievalHash) {
        return new PublicSnapshotFingerprint(
                "4.0", "2026-07-29.1", Map.of("PROJECT", 5, "CASE", 49),
                Set.of("claim-a:evidence-a"),
                Map.of("portfolio", portfolioHash, "retrieval", retrievalHash));
    }
}
