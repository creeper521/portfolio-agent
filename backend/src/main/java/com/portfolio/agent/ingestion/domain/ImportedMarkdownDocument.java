package com.portfolio.agent.ingestion.domain;

import java.util.List;
import java.util.Objects;

public final class ImportedMarkdownDocument {

    private final String relativePath;
    private final String contentHash;
    private final long byteSize;
    private final List<ImportedMarkdownChunk> chunks;
    private final MarkdownRevisionStatus revisionStatus;

    public ImportedMarkdownDocument(
            String relativePath, String contentHash, long byteSize,
            List<ImportedMarkdownChunk> chunks, MarkdownRevisionStatus revisionStatus) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.byteSize = byteSize;
        this.chunks = List.copyOf(chunks);
        this.revisionStatus = Objects.requireNonNull(revisionStatus, "revisionStatus");
        validateStatusConsistency();
    }

    public String getRelativePath() { return relativePath; }
    public String getContentHash() { return contentHash; }
    public long getByteSize() { return byteSize; }
    public List<ImportedMarkdownChunk> getChunks() { return chunks; }
    public MarkdownRevisionStatus getRevisionStatus() { return revisionStatus; }
    public boolean isReplaceCurrentRevision() { return revisionStatus.isReadyForCurrentRevision(); }

    private void validateStatusConsistency() {
        boolean hasPendingChunk = chunks.stream()
                .anyMatch(chunk -> chunk.getVectorStatus() == MarkdownVectorStatus.VECTOR_PENDING);
        if (revisionStatus == MarkdownRevisionStatus.PARSED && hasPendingChunk) {
            throw new IllegalArgumentException("parsed revision cannot contain pending vectors");
        }
        if (revisionStatus == MarkdownRevisionStatus.VECTOR_PENDING && !hasPendingChunk) {
            throw new IllegalArgumentException("pending revision must contain a pending vector");
        }
    }
}
