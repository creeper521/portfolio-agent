package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalDocumentEmbeddingBuilder {

    private final DocumentEmbeddingPort embeddingPort;
    private final int dimension;

    public LocalDocumentEmbeddingBuilder(DocumentEmbeddingPort embeddingPort, int dimension) {
        if (embeddingPort == null || dimension <= 0) {
            throw new IllegalArgumentException("document embedding configuration is invalid");
        }
        this.embeddingPort = embeddingPort;
        this.dimension = dimension;
    }

    public Map<String, float[]> build(List<RagDocument> documents) {
        if (documents == null) {
            throw new InvalidPortfolioSnapshotException("RAG documents are required");
        }
        Map<String, float[]> vectors = new LinkedHashMap<>();
        for (RagDocument document : documents.stream()
                .sorted(java.util.Comparator.comparing(RagDocument::getChunkId))
                .toList()) {
            float[] vector = embeddingPort.embedDocument(document.getText());
            validate(vector);
            if (vectors.put(document.getChunkId(), vector.clone()) != null) {
                throw new InvalidPortfolioSnapshotException(
                        "duplicate document vector chunkId: " + document.getChunkId());
            }
        }
        return java.util.Collections.unmodifiableMap(vectors);
    }

    private void validate(float[] vector) {
        if (vector == null || vector.length != dimension) {
            throw new InvalidPortfolioSnapshotException("document vector dimension mismatch");
        }
        double squaredNorm = 0.0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new InvalidPortfolioSnapshotException(
                        "document vector values must be finite");
            }
            squaredNorm += value * value;
        }
        if (Math.abs(Math.sqrt(squaredNorm) - 1.0) > 0.001) {
            throw new InvalidPortfolioSnapshotException(
                    "document vector must be L2 normalized");
        }
    }
}
