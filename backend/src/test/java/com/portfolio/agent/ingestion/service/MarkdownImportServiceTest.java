package com.portfolio.agent.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.ingestion.domain.ImportedMarkdownDocument;
import com.portfolio.agent.ingestion.domain.MarkdownChunk;
import com.portfolio.agent.ingestion.domain.MarkdownImportReport;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

class MarkdownImportServiceTest {

    @TempDir
    Path root;

    @Test
    void reusesOnlyUnchangedChunkEmbeddingsForAChangedDocument() throws Exception {
        Files.writeString(root.resolve("notes.md"), "# Kept\nsame\n\n# Changed\nnew", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of("notes.md", sha256("previous")));
        MarkdownChunk kept = new MarkdownChunker().chunk("# Kept\nsame").getFirst();
        store.reusable.put(kept.getHash(), vector());
        AtomicInteger embeddings = new AtomicInteger();
        DocumentEmbeddingPort embeddingPort = text -> {
            embeddings.incrementAndGet();
            return vector();
        };

        MarkdownImportReport report = new MarkdownImportService(
                store, embeddingPort, immediateTransactions()).importRoot(root);

        assertThat(report.getChanged()).isEqualTo(1);
        assertThat(report.getFailed()).isZero();
        assertThat(embeddings).hasValue(1);
        assertThat(store.saved).hasSize(1);
        assertThat(store.saved.getFirst().getChunks()).allSatisfy(chunk -> assertThat(chunk.getVectorStatus())
                .isEqualTo(com.portfolio.agent.ingestion.domain.MarkdownVectorStatus.READY));
    }

    @Test
    void keepsOldRevisionWhenEmbeddingFailsAndContinuesOtherDocuments() throws Exception {
        Files.writeString(root.resolve("first.md"), "first", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("second.md"), "second", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of("first.md", sha256("old")));
        DocumentEmbeddingPort embeddingPort = text -> {
            if (text.equals("first")) {
                throw new IllegalStateException("local embedding unavailable");
            }
            return vector();
        };

        MarkdownImportReport report = new MarkdownImportService(
                store, embeddingPort, immediateTransactions()).importRoot(root);

        assertThat(report.getChanged()).isEqualTo(1);
        assertThat(report.getAdded()).isEqualTo(1);
        assertThat(report.getVectorPending()).isEqualTo(1);
        assertThat(report.isPartial()).isTrue();
        assertThat(store.saved).hasSize(2);
        ImportedMarkdownDocument pending = store.saved.stream()
                .filter(document -> document.getRelativePath().equals("first.md"))
                .findFirst()
                .orElseThrow();
        assertThat(pending.isReplaceCurrentRevision()).isFalse();
        assertThat(pending.getChunks().getFirst().getVectorStatus())
                .isEqualTo(com.portfolio.agent.ingestion.domain.MarkdownVectorStatus.VECTOR_PENDING);
        assertThat(store.saved.stream()
                .filter(document -> document.getRelativePath().equals("second.md"))
                .findFirst()
                .orElseThrow()
                .isReplaceCurrentRevision()).isTrue();
    }

    @Test
    void marksMissingDocumentsWithoutDeletingTheirStoredRevision() throws Exception {
        InMemoryStore store = new InMemoryStore(Map.of("gone.md", sha256("old")));

        MarkdownImportReport report = new MarkdownImportService(
                store, text -> vector(), immediateTransactions()).importRoot(root);

        assertThat(report.getMissing()).isEqualTo(1);
        assertThat(store.documents).containsKey("gone.md");
        assertThat(store.missing).containsExactly("gone.md");
    }

    @Test
    void embedsEachNewChunkHashOnlyOnceWithinTheSameImport() throws Exception {
        Files.writeString(root.resolve("duplicate.md"), "repeat\n\nrepeat", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of());
        AtomicInteger embeddings = new AtomicInteger();

        new MarkdownImportService(store, text -> {
            embeddings.incrementAndGet();
            return vector();
        }, immediateTransactions()).importRoot(root);

        assertThat(embeddings).hasValue(1);
        assertThat(store.saved.getFirst().getChunks()).hasSize(2);
        assertThat(store.saved.getFirst().getChunks().getFirst().getEmbedding())
                .containsExactly(store.saved.getFirst().getChunks().get(1).getEmbedding());
    }

    @Test
    void cachesPendingOutcomeForAnInvalidVectorHashWithinTheSameImport() throws Exception {
        Files.writeString(root.resolve("duplicate.md"), "repeat\n\nrepeat", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of());
        AtomicInteger embeddings = new AtomicInteger();

        MarkdownImportReport report = new MarkdownImportService(store, text -> {
            embeddings.incrementAndGet();
            return new float[511];
        }, immediateTransactions()).importRoot(root);

        assertThat(embeddings).hasValue(1);
        assertThat(report.getVectorPending()).isEqualTo(1);
        assertThat(store.saved.getFirst().getChunks()).allSatisfy(chunk ->
                assertThat(chunk.getVectorStatus())
                        .isEqualTo(com.portfolio.agent.ingestion.domain.MarkdownVectorStatus.VECTOR_PENDING));
    }

    @Test
    void doesNotReportPendingVectorsWhenTransactionCompletionFails() throws Exception {
        Files.writeString(root.resolve("note.md"), "note", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of());

        MarkdownImportReport report = new MarkdownImportService(store, text -> new float[511], failingTransactions())
                .importRoot(root);

        assertThat(report.getFailed()).isEqualTo(1);
        assertThat(report.getAdded()).isZero();
        assertThat(report.getVectorPending()).isZero();
    }

    @Test
    void continuesWhenOneDocumentStoreTransactionFails() throws Exception {
        Files.writeString(root.resolve("first.md"), "first", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("second.md"), "second", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of());
        store.failingPath = "first.md";

        MarkdownImportReport report = new MarkdownImportService(store, text -> vector(), immediateTransactions()).importRoot(root);

        assertThat(report.getFailed()).isEqualTo(1);
        assertThat(report.getAdded()).isEqualTo(1);
        assertThat(store.saved).extracting(ImportedMarkdownDocument::getRelativePath).containsExactly("second.md");
    }

    @Test
    void retriesOnlyUnchangedDocumentsWhoseLatestRevisionIsPending() throws Exception {
        String pendingHash = sha256("pending");
        String readyHash = sha256("ready");
        Files.writeString(root.resolve("pending.md"), "pending", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("ready.md"), "ready", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("new.md"), "new", StandardCharsets.UTF_8);
        InMemoryStore store = new InMemoryStore(Map.of(
                "pending.md", pendingHash,
                "ready.md", readyHash,
                "missing.md", sha256("missing")));
        store.pending.add("pending.md");

        MarkdownImportReport report = new MarkdownImportService(
                store, text -> vector(), immediateTransactions()).importRoot(root, true);

        assertThat(report.getChanged()).isEqualTo(1);
        assertThat(report.getUnchanged()).isEqualTo(1);
        assertThat(report.getBlocked()).isEqualTo(1);
        assertThat(report.getMissing()).isEqualTo(1);
        assertThat(store.saved)
                .extracting(ImportedMarkdownDocument::getRelativePath)
                .containsExactly("pending.md");
        assertThat(store.missing).isEmpty();
        assertThat(store.saved.getFirst().isReplaceCurrentRevision()).isTrue();
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                return action.doInTransaction((TransactionStatus) null);
            }
        };
    }

    private TransactionOperations failingTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                action.doInTransaction((TransactionStatus) null);
                throw new IllegalStateException("commit failed");
            }
        };
    }

    private float[] vector() {
        float[] vector = new float[512];
        vector[0] = 1.0f;
        return vector;
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class InMemoryStore implements MarkdownGovernanceStore {
        private final Map<String, String> documents;
        private final Map<String, float[]> reusable = new HashMap<>();
        private final List<ImportedMarkdownDocument> saved = new ArrayList<>();
        private final List<String> missing = new ArrayList<>();
        private final Set<String> pending = new java.util.HashSet<>();
        private String failingPath;

        private InMemoryStore(Map<String, String> documents) {
            this.documents = new HashMap<>(documents);
        }

        @Override
        public Map<String, String> knownDocuments() {
            return Map.copyOf(documents);
        }

        @Override
        public Optional<String> contentHash(String relativePath) {
            return Optional.ofNullable(documents.get(relativePath));
        }

        @Override
        public Set<String> pendingDocuments() {
            return Set.copyOf(pending);
        }

        @Override
        public Map<String, float[]> reusableEmbeddings(String relativePath, Set<String> hashes) {
            Map<String, float[]> result = new HashMap<>();
            for (String hash : hashes) {
                if (reusable.containsKey(hash)) {
                    result.put(hash, reusable.get(hash));
                }
            }
            return result;
        }

        @Override
        public void saveRevision(ImportedMarkdownDocument document) {
            if (document.getRelativePath().equals(failingPath)) {
                throw new IllegalStateException("store failed");
            }
            saved.add(document);
            documents.put(document.getRelativePath(), document.getContentHash());
        }

        @Override
        public void markMissing(String relativePath) {
            missing.add(relativePath);
        }
    }
}
