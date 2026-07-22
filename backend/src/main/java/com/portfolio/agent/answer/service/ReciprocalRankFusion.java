package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.RetrievalCandidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion {

    public List<RetrievalCandidate> fuse(
            List<RankedRetrievalHit> keywordHits,
            List<RankedRetrievalHit> vectorHits,
            int k
    ) {
        Map<String, MutableRanks> ranks = new HashMap<>();
        for (RankedRetrievalHit hit : keywordHits) {
            ranks.computeIfAbsent(hit.getChunkId(), ignored -> new MutableRanks())
                    .keywordRank = hit.getRank();
        }
        for (RankedRetrievalHit hit : vectorHits) {
            ranks.computeIfAbsent(hit.getChunkId(), ignored -> new MutableRanks())
                    .vectorRank = hit.getRank();
        }
        List<RetrievalCandidate> result = new ArrayList<>();
        for (Map.Entry<String, MutableRanks> entry : ranks.entrySet()) {
            MutableRanks value = entry.getValue();
            double score = contribution(value.keywordRank, k)
                    + contribution(value.vectorRank, k);
            result.add(new RetrievalCandidate(
                    entry.getKey(), value.keywordRank, value.vectorRank, score));
        }
        result.sort(java.util.Comparator.comparingDouble(RetrievalCandidate::getFusedScore)
                .reversed().thenComparing(RetrievalCandidate::getChunkId));
        return List.copyOf(result);
    }

    private double contribution(Integer rank, int k) {
        return rank == null ? 0.0 : 1.0 / (k + rank);
    }

    private static final class MutableRanks {
        private Integer keywordRank;
        private Integer vectorRank;
    }
}
