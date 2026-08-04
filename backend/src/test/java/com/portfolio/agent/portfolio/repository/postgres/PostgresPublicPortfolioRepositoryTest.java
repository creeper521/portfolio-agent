package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class PostgresPublicPortfolioRepositoryTest {

    @Test
    void pinsTheActiveReleaseBeforeReadingItsPayloadAndCachesTheDecodedSnapshot() throws Exception {
        RuntimeContentSnapshot expected = snapshot("a".repeat(64));
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encoded = codec.encode(expected);
        PublicRuntimeSnapshotStore store = mock(PublicRuntimeSnapshotStore.class);
        when(store.findActiveRelease()).thenReturn(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"));
        when(store.findRuntimeSnapshot("release-a")).thenReturn(
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()));
        TransactionOperations transactions = immediateTransactions();
        PostgresPublicPortfolioRepository repository =
                new PostgresPublicPortfolioRepository(store, transactions, codec);

        RuntimeContentSnapshot first = repository.getSnapshot();
        RuntimeContentSnapshot second = repository.getSnapshot();

        assertThat(first).usingRecursiveComparison().isEqualTo(expected);
        assertThat(second).isSameAs(first);
        verify(store).findActiveRelease();
        verify(store).findRuntimeSnapshot("release-a");
    }

    @Test
    void keepsUsingTheFirstReleaseIdWhenTheActivePointerChangesBetweenQueries() throws Exception {
        RuntimeContentSnapshot releaseA = snapshot("a".repeat(64));
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encodedA = codec.encode(releaseA);
        SwitchingActiveReleaseStore store = new SwitchingActiveReleaseStore(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                new PublicReleaseMetadata("release-b", "version-b", "2.0", "b".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encodedA.getPayload(), encodedA.getChecksum()));

        RuntimeContentSnapshot loaded = new PostgresPublicPortfolioRepository(
                store, immediateTransactions(), codec).getSnapshot();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(releaseA);
        assertThat(store.getRequestedReleaseId()).isEqualTo("release-a");
        assertThat(store.getCurrentActiveReleaseId()).isEqualTo("release-b");
    }

    @Test
    void acceptsPostgresStyleNumericTextWhenItDecodesToTheSameDomainValue() throws Exception {
        RuntimeContentSnapshot expected = snapshot("a".repeat(64));
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encoded = codec.encode(expected);
        String postgresStylePayload = encoded.getPayload()
                .replace("\"averageDocumentLength\":1.0", "\"averageDocumentLength\":1e0");
        assertThat(postgresStylePayload).isNotEqualTo(encoded.getPayload());
        PublicRuntimeSnapshotStore store = fixedStore(
                expected, postgresStylePayload, encoded.getChecksum());

        RuntimeContentSnapshot loaded = new PostgresPublicPortfolioRepository(
                store, immediateTransactions(), codec).getSnapshot();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void rejectsARealNumericDomainChangeEvenWhenStoredJsonIsOtherwiseValid() throws Exception {
        RuntimeContentSnapshot expected = snapshot("a".repeat(64));
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encoded = codec.encode(expected);
        String changedPayload = encoded.getPayload()
                .replace("\"averageDocumentLength\":1.0", "\"averageDocumentLength\":2");
        assertThat(changedPayload).isNotEqualTo(encoded.getPayload());
        PublicRuntimeSnapshotStore store = fixedStore(
                expected, changedPayload, encoded.getChecksum());

        assertThatThrownBy(() -> new PostgresPublicPortfolioRepository(
                store, immediateTransactions(), codec).getSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void returnsARecursivelyEqualSnapshotForTheActualPublicBundle() throws Exception {
        RuntimeContentSnapshot expected = actualBundleSnapshot();
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encoded = codec.encode(expected);
        PublicRuntimeSnapshotStore store = mock(PublicRuntimeSnapshotStore.class);
        when(store.findActiveRelease()).thenReturn(new PublicReleaseMetadata(
                "release-a", expected.getContentVersion(), expected.getSchemaVersion(),
                databaseHash(expected.getRuntimeBundleHash()), "PUBLISHED"));
        when(store.findRuntimeSnapshot("release-a")).thenReturn(
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()));

        RuntimeContentSnapshot actual = new PostgresPublicPortfolioRepository(
                store, immediateTransactions(), codec).getSnapshot();

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(actual.getRetrievalContent().orElseThrow().getDocuments()).hasSize(83);
        assertThat(actual.getRetrievalContent().orElseThrow().getVectorIndex().getDimension()).isEqualTo(512);
    }

    @Test
    void failsClosedForMissingCorruptMismatchedOrUnpublishedData() throws Exception {
        RuntimeContentSnapshot expected = snapshot("a".repeat(64));
        PublicRuntimeSnapshotCodec codec = codec();
        EncodedRuntimeSnapshot encoded = codec.encode(expected);

        assertFailure(null, null, codec, "active");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "VERIFIED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()), codec, "PUBLISHED");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                null, codec, "payload");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), "b".repeat(64)), codec, "checksum");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "9.0", "a".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()), codec, "metadata");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "b".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()), codec, "metadata");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot("{broken", "a".repeat(64)), codec, "decode");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", null, "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()), codec, "hash");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "not-a-hash", "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), encoded.getChecksum()), codec, "hash");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), null), codec, "checksum");
        assertFailure(
                new PublicReleaseMetadata("release-a", "version-a", "2.0", "a".repeat(64), "PUBLISHED"),
                new StoredRuntimeSnapshot(encoded.getPayload(), "invalid"), codec, "checksum");
    }

    private void assertFailure(
            PublicReleaseMetadata release,
            StoredRuntimeSnapshot payload,
            PublicRuntimeSnapshotCodec codec,
            String message) {
        PublicRuntimeSnapshotStore store = mock(PublicRuntimeSnapshotStore.class);
        when(store.findActiveRelease()).thenReturn(release);
        if (release != null) {
            when(store.findRuntimeSnapshot(release.getReleaseId())).thenReturn(payload);
        }
        PostgresPublicPortfolioRepository repository =
                new PostgresPublicPortfolioRepository(store, immediateTransactions(), codec);

        assertThatThrownBy(repository::getSnapshot)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private PublicRuntimeSnapshotCodec codec() {
        return new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());
    }

    private String databaseHash(String value) {
        return value.startsWith("sha256:") ? value.substring(7) : value;
    }

    private PublicRuntimeSnapshotStore fixedStore(
            RuntimeContentSnapshot snapshot,
            String payload,
            String checksum) {
        PublicRuntimeSnapshotStore store = mock(PublicRuntimeSnapshotStore.class);
        when(store.findActiveRelease()).thenReturn(new PublicReleaseMetadata(
                "release-a", snapshot.getContentVersion(), snapshot.getSchemaVersion(),
                snapshot.getRuntimeBundleHash(), "PUBLISHED"));
        when(store.findRuntimeSnapshot("release-a")).thenReturn(
                new StoredRuntimeSnapshot(payload, checksum));
        return store;
    }

    private RuntimeContentSnapshot snapshot(String hash) throws Exception {
        PortfolioSnapshot content = new ObjectMapper().findAndRegisterModules().readValue("""
                {"schemaVersion":"2.0","contentVersion":"version-a","publishedAt":"2026-07-30T12:00:00+08:00",
                "owner":{"name":"","role":"Backend","summary":"Public","githubUrl":null,"email":null,"resumeUrl":null},
                "projects":[],"cases":[],"collections":[],"claims":[],"claimEvidenceLinks":[],
                "questionPresets":[],"evidence":[],"timelineEvents":[]}
                """, PortfolioSnapshot.class);
        RagDocument document = new RagDocument(
                "chunk-1", "version-a", List.of(), List.of(), List.of(),
                "public", List.of(), null, null, "c".repeat(64));
        RetrievalManifest manifest = new RetrievalManifest(
                "strategy", "normalization", "policy", "model", "artifact",
                512, 100, "unit", "cosine", 1, "d".repeat(64), "keyword", "vector");
        RuntimeRetrievalContent retrieval = new RuntimeRetrievalContent(
                manifest, List.of(document),
                new RuntimeKeywordIndex(1, 1.0, List.of(), Map.of()),
                new RuntimeVectorIndex(512, Map.of("chunk-1", new float[512])));
        return new RuntimeContentSnapshot(
                content, hash, Instant.parse("2026-07-30T04:00:00Z"), retrieval);
    }

    private TransactionOperations immediateTransactions() {
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private RuntimeContentSnapshot actualBundleSnapshot() throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String name) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertThat(stream).as("resource %s", name).isNotNull();
        try (InputStream input = stream) {
            return input.readAllBytes();
        }
    }

    private static final class SwitchingActiveReleaseStore implements PublicRuntimeSnapshotStore {
        private PublicReleaseMetadata current;
        private final PublicReleaseMetadata next;
        private final StoredRuntimeSnapshot releaseASnapshot;
        private String requestedReleaseId;

        private SwitchingActiveReleaseStore(
                PublicReleaseMetadata current,
                PublicReleaseMetadata next,
                StoredRuntimeSnapshot releaseASnapshot) {
            this.current = current;
            this.next = next;
            this.releaseASnapshot = releaseASnapshot;
        }

        @Override
        public PublicReleaseMetadata findActiveRelease() {
            PublicReleaseMetadata pinned = current;
            current = next;
            return pinned;
        }

        @Override
        public StoredRuntimeSnapshot findRuntimeSnapshot(String releaseId) {
            requestedReleaseId = releaseId;
            return "release-a".equals(releaseId) ? releaseASnapshot : null;
        }

        private String getRequestedReleaseId() {
            return requestedReleaseId;
        }

        private String getCurrentActiveReleaseId() {
            return current.getReleaseId();
        }
    }
}
