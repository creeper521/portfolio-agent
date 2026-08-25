package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回答检索的关键词索引统计（不可变值对象），供 BM25 类关键词评分使用。
 *
 * <p>包含文档（分块）数量、平均文档长度、每个文档的词频表，
 * 以及词项到出现文档数（document frequency）的映射。
 */
public final class AnswerKeywordIndex {

    private final int documentCount;
    private final double averageDocumentLength;
    private final List<DocumentEntry> documents;
    private final Map<String, Integer> documentFrequencies;

    public AnswerKeywordIndex(
            int documentCount,
            double averageDocumentLength,
            List<DocumentEntry> documents,
            Map<String, Integer> documentFrequencies
    ) {
        this.documentCount = documentCount;
        this.averageDocumentLength = averageDocumentLength;
        this.documents = List.copyOf(documents);
        this.documentFrequencies = Collections.unmodifiableMap(
                new LinkedHashMap<>(documentFrequencies));
    }

    public int getDocumentCount() { return documentCount; }
    public double getAverageDocumentLength() { return averageDocumentLength; }
    public List<DocumentEntry> getDocuments() { return documents; }
    public Map<String, Integer> getDocumentFrequencies() { return documentFrequencies; }

    /** 单个文档（分块）的关键词统计：分块 ID、文档长度与词频表。 */
    public static final class DocumentEntry {
        private final String chunkId;
        private final int documentLength;
        private final Map<String, Integer> termFrequencies;

        public DocumentEntry(
                String chunkId,
                int documentLength,
                Map<String, Integer> termFrequencies
        ) {
            this.chunkId = chunkId;
            this.documentLength = documentLength;
            this.termFrequencies = Collections.unmodifiableMap(
                    new LinkedHashMap<>(termFrequencies));
        }

        public String getChunkId() { return chunkId; }
        public int getDocumentLength() { return documentLength; }
        public Map<String, Integer> getTermFrequencies() { return termFrequencies; }
    }
}
