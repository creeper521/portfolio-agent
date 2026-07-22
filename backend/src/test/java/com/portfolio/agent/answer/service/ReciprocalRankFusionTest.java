package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

    @Test
    void favorsDualRouteHitsWithoutAddingRawScores() {
        List<RetrievalCandidate> fused = new ReciprocalRankFusion().fuse(
                List.of(hit("keyword-only", 1, 99.0), hit("both", 2, 0.1)),
                List.of(hit("vector-only", 1, 0.99), hit("both", 2, 0.56)),
                60);

        assertThat(fused).extracting(RetrievalCandidate::getChunkId)
                .containsExactly("both", "keyword-only", "vector-only");
        assertThat(fused.get(0).getKeywordRank()).isEqualTo(2);
        assertThat(fused.get(0).getVectorRank()).isEqualTo(2);
    }

    @Test
    void breaksEqualFusionScoresByChunkId() {
        List<RetrievalCandidate> fused = new ReciprocalRankFusion().fuse(
                List.of(hit("chunk-b", 1, 2.0), hit("chunk-a", 1, 1.0)),
                List.of(), 60);

        assertThat(fused).extracting(RetrievalCandidate::getChunkId)
                .containsExactly("chunk-a", "chunk-b");
    }

    private RankedRetrievalHit hit(String id, int rank, double score) {
        return new RankedRetrievalHit(id, rank, score);
    }
}
