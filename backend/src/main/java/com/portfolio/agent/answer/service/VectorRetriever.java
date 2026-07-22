package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.EmbeddingVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VectorRetriever {

    public List<RankedRetrievalHit> retrieve(
            EmbeddingVector query,
            Map<String, float[]> documentVectors,
            int topK,
            double threshold
    ) {
        return retrieve(query, documentVectors, documentVectors.keySet(), topK, threshold);
    }

    public List<RankedRetrievalHit> retrieve(
            EmbeddingVector query,
            Map<String, float[]> documentVectors,
            Set<String> allowedChunkIds,
            int topK,
            double threshold
    ) {
        if (topK <= 0) {
            return List.of();
        }
        float[] queryValues = query.copyValues();
        List<ScoredChunk> scored = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : documentVectors.entrySet()) {
            if (!allowedChunkIds.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue().length != queryValues.length) {
                throw new LocalEmbeddingFailureException("VECTOR_DIMENSION_MISMATCH");
            }
            double similarity = 0.0;
            for (int index = 0; index < queryValues.length; index++) {
                similarity += queryValues[index] * entry.getValue()[index];
            }
            if (similarity >= threshold) {
                scored.add(new ScoredChunk(entry.getKey(), similarity));
            }
        }
        scored.sort(java.util.Comparator.comparingDouble(ScoredChunk::getScore).reversed()
                .thenComparing(ScoredChunk::getChunkId));
        List<RankedRetrievalHit> hits = new ArrayList<>();
        int limit = Math.min(topK, scored.size());
        for (int index = 0; index < limit; index++) {
            ScoredChunk item = scored.get(index);
            hits.add(new RankedRetrievalHit(item.getChunkId(), index + 1, item.getScore()));
        }
        return List.copyOf(hits);
    }

    private static final class ScoredChunk {
        private final String chunkId;
        private final double score;

        private ScoredChunk(String chunkId, double score) {
            this.chunkId = chunkId;
            this.score = score;
        }

        private String getChunkId() { return chunkId; }
        private double getScore() { return score; }
    }
}
