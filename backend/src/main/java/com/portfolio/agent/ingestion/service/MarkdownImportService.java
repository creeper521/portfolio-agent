package com.portfolio.agent.ingestion.service;

import com.portfolio.agent.ingestion.domain.ImportedMarkdownChunk;
import com.portfolio.agent.ingestion.domain.ImportedMarkdownDocument;
import com.portfolio.agent.ingestion.domain.MarkdownChunk;
import com.portfolio.agent.ingestion.domain.MarkdownImportReport;
import com.portfolio.agent.ingestion.domain.MarkdownRevisionStatus;
import com.portfolio.agent.ingestion.domain.MarkdownScanEntry;
import com.portfolio.agent.ingestion.domain.SourceDocumentStatus;
import com.portfolio.agent.ingestion.domain.MarkdownVectorStatus;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.support.TransactionOperations;

public final class MarkdownImportService {

    private static final int VECTOR_DIMENSION = 512;

    private final MarkdownGovernanceStore store;
    private final DocumentEmbeddingPort embeddingPort;
    private final TransactionOperations transactions;
    private final MarkdownChunker chunker;

    public MarkdownImportService(
            MarkdownGovernanceStore store, DocumentEmbeddingPort embeddingPort, TransactionOperations transactions) {
        this(store, embeddingPort, transactions, new MarkdownChunker());
    }

    MarkdownImportService(
            MarkdownGovernanceStore store, DocumentEmbeddingPort embeddingPort,
            TransactionOperations transactions, MarkdownChunker chunker) {
        this.store = Objects.requireNonNull(store, "store");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
    }

    public MarkdownImportReport importRoot(Path root) {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        List<MarkdownScanEntry> entries = new MarkdownScanService(store).scan(normalizedRoot).getEntries();
        Counts counts = new Counts();
        for (MarkdownScanEntry entry : entries) {
            switch (entry.getStatus()) {
                case ADDED, CHANGED -> importOne(normalizedRoot, entry, counts);
                case MISSING -> markMissing(entry, counts);
                case UNCHANGED -> counts.unchanged++;
                case FAILED -> counts.failed++;
                case BLOCKED -> counts.blocked++;
            }
        }
        return counts.report();
    }

    private void importOne(Path root, MarkdownScanEntry entry, Counts counts) {
        try {
            ImportedMarkdownDocument document = transactions.execute(status -> {
                byte[] content = readContainedFile(root, entry.getRelativePath());
                List<MarkdownChunk> chunks = chunker.chunk(new String(content, StandardCharsets.UTF_8));
                ImportedMarkdownDocument imported = toImportedDocument(entry, content.length, chunks);
                store.saveRevision(imported);
                return imported;
            });
            if (document == null) {
                throw new IllegalStateException("governance import transaction returned no document");
            }
            if (entry.getStatus() == SourceDocumentStatus.ADDED) {
                counts.added++;
            } else {
                counts.changed++;
            }
            if (document.getRevisionStatus() == MarkdownRevisionStatus.VECTOR_PENDING) {
                counts.vectorPending++;
            }
        } catch (RuntimeException exception) {
            counts.failed++;
        }
    }

    private void markMissing(MarkdownScanEntry entry, Counts counts) {
        try {
            transactions.execute(status -> {
                store.markMissing(entry.getRelativePath());
                return null;
            });
            counts.missing++;
        } catch (RuntimeException exception) {
            counts.failed++;
        }
    }

    private ImportedMarkdownDocument toImportedDocument(
            MarkdownScanEntry entry, long byteSize, List<MarkdownChunk> chunks) {
        Set<String> hashes = new HashSet<>();
        for (MarkdownChunk chunk : chunks) {
            hashes.add(chunk.getHash());
        }
        Map<String, EmbeddingOutcome> outcomes = new HashMap<>();
        for (Map.Entry<String, float[]> reusable : store.reusableEmbeddings(entry.getRelativePath(), hashes).entrySet()) {
            outcomes.put(reusable.getKey(), EmbeddingOutcome.ready(reusable.getValue()));
        }
        List<ImportedMarkdownChunk> imported = new ArrayList<>();
        boolean allVectorsReady = true;
        for (MarkdownChunk chunk : chunks) {
            EmbeddingOutcome outcome = outcomes.get(chunk.getHash());
            if (outcome == null) {
                float[] vector = null;
                try {
                    vector = embeddingPort.embedDocument(chunk.getText());
                } catch (RuntimeException exception) {
                    // A failed outcome is cached for this hash for the rest of the import.
                }
                outcome = isValidVector(vector) ? EmbeddingOutcome.ready(vector) : EmbeddingOutcome.pending();
                outcomes.put(chunk.getHash(), outcome);
            }
            if (outcome.isReady()) {
                imported.add(new ImportedMarkdownChunk(
                        chunk.getOrdinal(), chunk.getHash(), chunk.getText(), outcome.getVector(), MarkdownVectorStatus.READY));
            } else {
                allVectorsReady = false;
                imported.add(new ImportedMarkdownChunk(
                        chunk.getOrdinal(), chunk.getHash(), chunk.getText(), null, MarkdownVectorStatus.VECTOR_PENDING));
            }
        }
        return new ImportedMarkdownDocument(
                entry.getRelativePath(), entry.getContentHash(), byteSize, imported,
                allVectorsReady ? MarkdownRevisionStatus.PARSED : MarkdownRevisionStatus.VECTOR_PENDING);
    }

    private byte[] readContainedFile(Path root, String relativePath) {
        try {
            Path realRoot = root.toRealPath();
            Path candidate = root.resolve(relativePath).normalize();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot) || !realFile.getFileName().toString()
                    .toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
                throw new IllegalStateException("markdown file is outside supplied root");
            }
            return Files.readAllBytes(realFile);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read markdown file", exception);
        }
    }

    private boolean isValidVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            return false;
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static final class Counts {
        private int added;
        private int changed;
        private int unchanged;
        private int missing;
        private int failed;
        private int blocked;
        private int vectorPending;

        private MarkdownImportReport report() {
            return new MarkdownImportReport(added, changed, unchanged, missing, failed, blocked, vectorPending);
        }
    }

    private static final class EmbeddingOutcome {
        private final float[] vector;

        private EmbeddingOutcome(float[] vector) {
            this.vector = vector == null ? null : vector.clone();
        }

        private static EmbeddingOutcome ready(float[] vector) {
            return new EmbeddingOutcome(vector);
        }

        private static EmbeddingOutcome pending() {
            return new EmbeddingOutcome(null);
        }

        private boolean isReady() {
            return vector != null;
        }

        private float[] getVector() {
            return vector == null ? null : vector.clone();
        }
    }
}
