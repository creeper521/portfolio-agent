package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorRetrieverTest {

    @Test
    void ranksCosineCandidatesAboveThresholdWithStableTies() {
        Map<String, float[]> vectors = new LinkedHashMap<>();
        vectors.put("chunk-b", new float[]{1.0f, 0.0f});
        vectors.put("chunk-a", new float[]{1.0f, 0.0f});
        vectors.put("chunk-c", new float[]{0.0f, 1.0f});

        List<RankedRetrievalHit> hits = new VectorRetriever().retrieve(
                new EmbeddingVector(new float[]{1.0f, 0.0f}), vectors, 8, 0.55);

        assertThat(hits).extracting(RankedRetrievalHit::getChunkId)
                .containsExactly("chunk-a", "chunk-b");
        assertThat(hits).extracting(RankedRetrievalHit::getRank)
                .containsExactly(1, 2);
    }
}
