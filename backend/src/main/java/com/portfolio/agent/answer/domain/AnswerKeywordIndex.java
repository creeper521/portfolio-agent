package com.portfolio.agent.answer.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
