package com.portfolio.agent.portfolio.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词检索索引：供 BM25 式关键词召回使用的预计算统计量。
 *
 * <p>documentCount 与 averageDocumentLength 是全局统计；documents 逐块保存词频；
 * documentFrequencies 保存每个词出现的切块数。这些统计量在发布构建时一次性算好，
 * 运行时只做打分、不再分词统计。构造时对集合与映射做防御性复制并封装为不可变视图。
 */
public final class RuntimeKeywordIndex {

    private final int documentCount;
    private final double averageDocumentLength;
    private final List<DocumentEntry> documents;
    private final Map<String, Integer> documentFrequencies;

    public RuntimeKeywordIndex(
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

    /**
     * 单个切块的关键词统计：chunkId 定位切块，documentLength 为切块长度（词数），
     * termFrequencies 为切块内每个词的出现次数。
     */
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
