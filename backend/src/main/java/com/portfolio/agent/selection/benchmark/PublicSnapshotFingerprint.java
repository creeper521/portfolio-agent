package com.portfolio.agent.selection.benchmark;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PublicSnapshotFingerprint {
    private final String schemaVersion;
    private final String contentVersion;
    private final Map<String, Integer> semanticCounts;
    private final Set<String> relationships;
    private final Map<String, String> canonicalHashes;

    public PublicSnapshotFingerprint(
            String schemaVersion, String contentVersion, Map<String, Integer> semanticCounts,
            Set<String> relationships, Map<String, String> canonicalHashes) {
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.semanticCounts = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(semanticCounts, "semanticCounts")));
        this.relationships = Set.copyOf(new TreeSet<>(
                Objects.requireNonNull(relationships, "relationships")));
        this.canonicalHashes = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(canonicalHashes, "canonicalHashes")));
        if (this.semanticCounts.isEmpty() || this.relationships.isEmpty()
                || this.canonicalHashes.isEmpty()) {
            throw new IllegalArgumentException("public fingerprint must contain counts, relationships, and hashes");
        }
    }

    public String getSchemaVersion() { return schemaVersion; }
    public String getContentVersion() { return contentVersion; }
    public Map<String, Integer> getSemanticCounts() { return semanticCounts; }
    public Set<String> getRelationships() { return relationships; }
    public Map<String, String> getCanonicalHashes() { return canonicalHashes; }
}
