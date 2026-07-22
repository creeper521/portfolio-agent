package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKeywordIndex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KeywordRetriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public List<RankedRetrievalHit> retrieve(
            AnswerKeywordIndex index,
            List<String> queryTerms,
            int topK
    ) {
        Set<String> allChunkIds = index.getDocuments().stream()
                .map(AnswerKeywordIndex.DocumentEntry::getChunkId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return retrieve(index, queryTerms, allChunkIds, topK);
    }

    public List<RankedRetrievalHit> retrieve(
            AnswerKeywordIndex index,
            List<String> queryTerms,
            Set<String> allowedChunkIds,
            int topK
    ) {
        if (topK <= 0 || index.getDocumentCount() == 0) {
            return List.of();
        }
        Set<String> uniqueTerms = new LinkedHashSet<>(queryTerms);
        List<ScoredChunk> scored = new ArrayList<>();
        for (AnswerKeywordIndex.DocumentEntry document : index.getDocuments()) {
            if (!allowedChunkIds.contains(document.getChunkId())) {
                continue;
            }
            double score = 0.0;
            for (String term : uniqueTerms) {
                int frequency = document.getTermFrequencies().getOrDefault(term, 0);
                int documentFrequency = index.getDocumentFrequencies().getOrDefault(term, 0);
                if (frequency == 0 || documentFrequency == 0) {
                    continue;
                }
                double inverseDocumentFrequency = Math.log(
                        1.0 + (index.getDocumentCount() - documentFrequency + 0.5)
                                / (documentFrequency + 0.5));
                double lengthRatio = index.getAverageDocumentLength() == 0.0
                        ? 0.0
                        : document.getDocumentLength() / index.getAverageDocumentLength();
                double denominator = frequency + K1 * (1.0 - B + B * lengthRatio);
                score += inverseDocumentFrequency * frequency * (K1 + 1.0) / denominator;
            }
            if (score > 0.0) {
                scored.add(new ScoredChunk(document.getChunkId(), score));
            }
        }
        scored.sort(java.util.Comparator.comparingDouble(ScoredChunk::getScore).reversed()
                .thenComparing(ScoredChunk::getChunkId));
        List<RankedRetrievalHit> result = new ArrayList<>();
        int limit = Math.min(topK, scored.size());
        for (int indexPosition = 0; indexPosition < limit; indexPosition++) {
            ScoredChunk item = scored.get(indexPosition);
            result.add(new RankedRetrievalHit(
                    item.getChunkId(), indexPosition + 1, item.getScore()));
        }
        return List.copyOf(result);
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
