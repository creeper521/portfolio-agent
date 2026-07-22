package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentEmbeddingBuilderTest {

    @Test
    void embedsRawDocumentTextWithoutAQueryInstructionAndOrdersByChunkId() {
        List<String> embeddedTexts = new ArrayList<>();
        DocumentEmbeddingPort port = text -> {
            embeddedTexts.add(text);
            return text.equals("first public fact")
                    ? new float[]{1.0f, 0.0f}
                    : new float[]{0.0f, 1.0f};
        };
        LocalDocumentEmbeddingBuilder builder = new LocalDocumentEmbeddingBuilder(port, 2);

        Map<String, float[]> vectors = builder.build(List.of(
                document("chunk-b", "second public fact"),
                document("chunk-a", "first public fact")));

        assertThat(vectors.keySet()).containsExactly("chunk-a", "chunk-b");
        assertThat(embeddedTexts).containsExactly("first public fact", "second public fact");
        assertThat(vectors.get("chunk-a")).containsExactly(1.0f, 0.0f);
    }

    @Test
    void rejectsWrongDimensionAndNonNormalizedDocumentVectors() {
        assertThatThrownBy(() -> new LocalDocumentEmbeddingBuilder(
                ignored -> new float[]{1.0f}, 2).build(List.of(document("chunk-a", "fact"))))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("dimension");
        assertThatThrownBy(() -> new LocalDocumentEmbeddingBuilder(
                ignored -> new float[]{1.0f, 1.0f}, 2)
                .build(List.of(document("chunk-a", "fact"))))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("normalized");
    }

    private RagDocument document(String id, String text) {
        RagDocument unsigned = new RagDocument(
                id, "2026-07-22.1", List.of("sql-audit"), List.of("claim-1"),
                text, List.of("audit"), LocalDate.of(2026, 7, 22), null, "unsigned");
        return new RagDocument(
                id, unsigned.getContentVersion(), unsigned.getProjectSlugs(),
                unsigned.getClaimIds(), text, unsigned.getTopics(), unsigned.getValidFrom(),
                null, RagDocumentHashCalculator.contentHash(unsigned));
    }
}
