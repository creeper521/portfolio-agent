package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeywordIndexFile {

    private final String formatVersion;
    private final String normalizationVersion;
    private final int documentCount;
    private final double averageDocumentLength;
    private final List<DocumentEntry> documents;
    private final Map<String, Integer> documentFrequencies;

    @JsonCreator
    public KeywordIndexFile(
            @JsonProperty("formatVersion") String formatVersion,
            @JsonProperty("normalizationVersion") String normalizationVersion,
            @JsonProperty("documentCount") int documentCount,
            @JsonProperty("averageDocumentLength") double averageDocumentLength,
            @JsonProperty("documents") List<DocumentEntry> documents,
            @JsonProperty("documentFrequencies") Map<String, Integer> documentFrequencies
    ) {
        this.formatVersion = formatVersion;
        this.normalizationVersion = normalizationVersion;
        this.documentCount = documentCount;
        this.averageDocumentLength = averageDocumentLength;
        this.documents = List.copyOf(documents);
        this.documentFrequencies = Collections.unmodifiableMap(
                new LinkedHashMap<>(documentFrequencies));
    }

    public String getFormatVersion() { return formatVersion; }
    public String getNormalizationVersion() { return normalizationVersion; }
    public int getDocumentCount() { return documentCount; }
    public double getAverageDocumentLength() { return averageDocumentLength; }
    public List<DocumentEntry> getDocuments() { return documents; }
    public Map<String, Integer> getDocumentFrequencies() { return documentFrequencies; }

    public static final class DocumentEntry {
        private final String chunkId;
        private final int documentLength;
        private final Map<String, Integer> termFrequencies;

        @JsonCreator
        public DocumentEntry(
                @JsonProperty("chunkId") String chunkId,
                @JsonProperty("documentLength") int documentLength,
                @JsonProperty("termFrequencies") Map<String, Integer> termFrequencies
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
