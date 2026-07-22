package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordRetrieverTest {

    @Test
    void ranksBm25MatchesAndUsesChunkIdForStableTies() {
        AnswerKeywordIndex index = new AnswerKeywordIndex(
                3, 2.0,
                List.of(
                        entry("chunk-b", Map.of("java", 1, "交付", 1)),
                        entry("chunk-a", Map.of("java", 1, "交付", 1)),
                        entry("chunk-c", Map.of("python", 2))),
                orderedMap(Map.entry("java", 2), Map.entry("交付", 2), Map.entry("python", 1)));

        List<RankedRetrievalHit> hits = new KeywordRetriever()
                .retrieve(index, List.of("java", "交付"), 8);

        assertThat(hits).extracting(RankedRetrievalHit::getChunkId)
                .containsExactly("chunk-a", "chunk-b");
        assertThat(hits).extracting(RankedRetrievalHit::getRank)
                .containsExactly(1, 2);
    }

    @Test
    void enforcesTopKAndReturnsNoZeroScoreDocuments() {
        AnswerKeywordIndex index = new AnswerKeywordIndex(
                3, 1.0,
                List.of(
                        entry("chunk-a", Map.of("java", 2)),
                        entry("chunk-b", Map.of("java", 1)),
                        entry("chunk-c", Map.of("python", 1))),
                orderedMap(Map.entry("java", 2), Map.entry("python", 1)));

        List<RankedRetrievalHit> hits = new KeywordRetriever()
                .retrieve(index, List.of("java"), 1);

        assertThat(hits).extracting(RankedRetrievalHit::getChunkId)
                .containsExactly("chunk-a");
    }

    private AnswerKeywordIndex.DocumentEntry entry(String id, Map<String, Integer> terms) {
        return new AnswerKeywordIndex.DocumentEntry(
                id, terms.values().stream().mapToInt(Integer::intValue).sum(), terms);
    }

    @SafeVarargs
    private final Map<String, Integer> orderedMap(Map.Entry<String, Integer>... entries) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }
}
