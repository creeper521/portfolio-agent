package com.portfolio.agent.portfolio.repository.postgres;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import java.util.Locale;
import java.util.Objects;
import org.springframework.transaction.support.TransactionOperations;

public final class PostgresPublicPortfolioRepository implements PublicPortfolioRepository {

    private final PublicRuntimeSnapshotStore store;
    private final TransactionOperations transactions;
    private final PublicRuntimeSnapshotCodec codec;
    private volatile RuntimeContentSnapshot cachedSnapshot;

    PostgresPublicPortfolioRepository(
            PublicRuntimeSnapshotStore store,
            TransactionOperations transactions,
            PublicRuntimeSnapshotCodec codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public RuntimeContentSnapshot getSnapshot() {
        RuntimeContentSnapshot snapshot = cachedSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cachedSnapshot == null) {
                cachedSnapshot = transactions.execute(status -> loadPinnedSnapshot());
                if (cachedSnapshot == null) {
                    throw new IllegalStateException("public snapshot transaction returned no data");
                }
            }
            return cachedSnapshot;
        }
    }

    private RuntimeContentSnapshot loadPinnedSnapshot() {
        PublicReleaseMetadata release = store.findActiveRelease();
        if (release == null) {
            throw new IllegalStateException("active public release is missing");
        }
        if (!"PUBLISHED".equals(release.getStatus())) {
            throw new IllegalStateException("active public release must be PUBLISHED");
        }
        String releaseHash = validatedDatabaseHash(
                release.getContentHash(), "public release content hash");
        StoredRuntimeSnapshot stored = store.findRuntimeSnapshot(release.getReleaseId());
        if (stored == null) {
            throw new IllegalStateException("public runtime snapshot payload is missing");
        }
        String storedChecksum = validatedDatabaseHash(
                stored.getChecksum(), "public runtime snapshot checksum");
        RuntimeContentSnapshot decoded;
        try {
            decoded = codec.decode(stored.getPayload());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("unable to decode public runtime snapshot", exception);
        }
        String semanticChecksum = codec.encode(decoded).getChecksum();
        if (!constantTimeEquals(semanticChecksum, storedChecksum)) {
            throw new IllegalStateException("public runtime snapshot checksum mismatch");
        }
        if (!release.getReleaseVersion().equals(decoded.getContentVersion())
                || !release.getSchemaVersion().equals(decoded.getSchemaVersion())
                || !releaseHash.equals(validatedRuntimeHash(
                        decoded.getRuntimeBundleHash(), "runtime bundle hash"))) {
            throw new IllegalStateException("public runtime snapshot metadata mismatch");
        }
        return decoded;
    }

    private String validatedDatabaseHash(String value, String label) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException(label + " must be a 64-character hexadecimal hash");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String validatedRuntimeHash(String value, String label) {
        if (value == null) {
            throw new IllegalStateException(label + " must be a 64-character hexadecimal hash");
        }
        String normalized = value.startsWith("sha256:") ? value.substring(7) : value;
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException(label + " must be a 64-character hexadecimal hash");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
