package com.portfolio.agent.release.benchmark.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares public snapshot semantics only. Each schema/content version, named count,
 * relationship set and canonical public hash is one equally weighted assertion.
 * A score of {@code 1.0000} is possible only when every assertion matches.
 */
public final class MigrationIntegrityEvaluator {
    public MigrationIntegrityResult compare(
            PublicSnapshotFingerprint file, PublicSnapshotFingerprint database) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(database, "database");
        List<String> mismatches = new ArrayList<>();
        int assertions = 2;
        int matches = 0;
        if (file.getSchemaVersion().equals(database.getSchemaVersion())) {
            matches++;
        } else {
            mismatches.add("schemaVersion");
        }
        if (file.getContentVersion().equals(database.getContentVersion())) {
            matches++;
        } else {
            mismatches.add("contentVersion");
        }
        Set<String> countKeys = union(file.getSemanticCounts().keySet(), database.getSemanticCounts().keySet());
        for (String key : countKeys) {
            assertions++;
            if (Objects.equals(file.getSemanticCounts().get(key), database.getSemanticCounts().get(key))) {
                matches++;
            } else {
                mismatches.add("count:" + key);
            }
        }
        assertions++;
        if (file.getRelationships().equals(database.getRelationships())) {
            matches++;
        } else {
            mismatches.add("relationships");
        }
        Set<String> hashKeys = union(file.getCanonicalHashes().keySet(), database.getCanonicalHashes().keySet());
        for (String key : hashKeys) {
            assertions++;
            if (Objects.equals(file.getCanonicalHashes().get(key), database.getCanonicalHashes().get(key))) {
                matches++;
            } else {
                mismatches.add("hash:" + key);
            }
        }
        mismatches.sort(String::compareTo);
        double score = (double) matches / assertions;
        return new MigrationIntegrityResult(score, mismatches.isEmpty(), mismatches);
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> values = new TreeSet<>(left);
        values.addAll(right);
        return values;
    }
}
