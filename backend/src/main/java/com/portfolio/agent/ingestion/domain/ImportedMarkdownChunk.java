package com.portfolio.agent.ingestion.domain;

import java.util.Objects;

public final class ImportedMarkdownChunk {

    private final int ordinal;
    private final String hash;
    private final String privateText;
    private final float[] embedding;
    private final MarkdownVectorStatus vectorStatus;

    public ImportedMarkdownChunk(
            int ordinal, String hash, String privateText, float[] embedding,
            MarkdownVectorStatus vectorStatus) {
        this.ordinal = ordinal;
        this.hash = Objects.requireNonNull(hash, "hash");
        this.privateText = Objects.requireNonNull(privateText, "privateText");
        this.vectorStatus = Objects.requireNonNull(vectorStatus, "vectorStatus");
        validateEmbedding(embedding, vectorStatus);
        this.embedding = embedding == null ? null : embedding.clone();
    }

    public int getOrdinal() { return ordinal; }
    public String getHash() { return hash; }
    public String getPrivateText() { return privateText; }
    public float[] getEmbedding() { return embedding == null ? null : embedding.clone(); }
    public MarkdownVectorStatus getVectorStatus() { return vectorStatus; }

    private void validateEmbedding(float[] value, MarkdownVectorStatus status) {
        if (status == MarkdownVectorStatus.VECTOR_PENDING && value != null) {
            throw new IllegalArgumentException("pending vector must be absent");
        }
        if (status == MarkdownVectorStatus.READY) {
            if (value == null || value.length != 512) {
                throw new IllegalArgumentException("ready vector must have dimension 512");
            }
            for (float item : value) {
                if (!Float.isFinite(item)) {
                    throw new IllegalArgumentException("ready vector must be finite");
                }
            }
        }
    }
}
