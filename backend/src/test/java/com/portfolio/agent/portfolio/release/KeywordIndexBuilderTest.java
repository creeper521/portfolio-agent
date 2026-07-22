package com.portfolio.agent.portfolio.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIndexBuilderTest {

    private final KeywordIndexBuilder builder = new KeywordIndexBuilder();

    @Test
    void normalizesUnicodeEnglishNumbersAndChineseBigrams() {
        KeywordIndexFile index = builder.build(List.of(
                document("chunk-1", "ＳＱＬ Audit 2026，完成交付")));

        assertThat(index.getDocuments().get(0).getTermFrequencies())
                .containsEntry("sql", 1)
                .containsEntry("audit", 1)
                .containsEntry("2026", 1)
                .containsEntry("完成", 1)
                .containsEntry("成交", 1)
                .containsEntry("交付", 1);
    }

    @Test
    void calculatesStableDocumentFrequencyAndDropsStopWords() {
        KeywordIndexFile index = builder.build(List.of(
                document("chunk-b", "项目 Java 交付"),
                document("chunk-a", "这个项目 Java 验证")));

        assertThat(index.getDocuments()).extracting("chunkId")
                .containsExactly("chunk-a", "chunk-b");
        assertThat(index.getDocumentFrequencies())
                .containsEntry("java", 2)
                .doesNotContainKeys("项目", "这个");
    }

    @Test
    void serializesIdenticalBytesRegardlessOfInputOrder() throws Exception {
        RagDocument first = document("chunk-a", "Java 验证");
        RagDocument second = document("chunk-b", "SQL 交付");
        ObjectMapper mapper = new ObjectMapper();

        byte[] ordered = mapper.writeValueAsBytes(builder.build(List.of(first, second)));
        byte[] reversed = mapper.writeValueAsBytes(builder.build(List.of(second, first)));

        assertThat(reversed).isEqualTo(ordered);
    }

    private RagDocument document(String id, String text) {
        return new RagDocument(
                id, "2026-07-21.1", List.of("sql-audit"), List.of("claim-1"),
                text, List.of("DELIVERY"), LocalDate.parse("2026-07-01"),
                null, "sha256:test");
    }
}
