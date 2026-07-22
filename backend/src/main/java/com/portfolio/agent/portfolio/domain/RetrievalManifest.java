package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RetrievalManifest {

    private final String strategyVersion;
    private final String normalizationVersion;
    private final String retrievalPolicyVersion;
    private final String embeddingModelId;
    private final String embeddingArtifactSha256;
    private final int dimension;
    private final int documentMaxTokens;
    private final String vectorNormalization;
    private final String similarity;
    private final int chunkCount;
    private final String chunkSetHash;
    private final String keywordIndexFormatVersion;
    private final String vectorIndexFormatVersion;

    @JsonCreator
    public RetrievalManifest(
            @JsonProperty("strategyVersion") String strategyVersion,
            @JsonProperty("normalizationVersion") String normalizationVersion,
            @JsonProperty("retrievalPolicyVersion") String retrievalPolicyVersion,
            @JsonProperty("embeddingModelId") String embeddingModelId,
            @JsonProperty("embeddingArtifactSha256") String embeddingArtifactSha256,
            @JsonProperty("dimension") int dimension,
            @JsonProperty("documentMaxTokens") int documentMaxTokens,
            @JsonProperty("vectorNormalization") String vectorNormalization,
            @JsonProperty("similarity") String similarity,
            @JsonProperty("chunkCount") int chunkCount,
            @JsonProperty("chunkSetHash") String chunkSetHash,
            @JsonProperty("keywordIndexFormatVersion") String keywordIndexFormatVersion,
            @JsonProperty("vectorIndexFormatVersion") String vectorIndexFormatVersion
    ) {
        this.strategyVersion = strategyVersion;
        this.normalizationVersion = normalizationVersion;
        this.retrievalPolicyVersion = retrievalPolicyVersion;
        this.embeddingModelId = embeddingModelId;
        this.embeddingArtifactSha256 = embeddingArtifactSha256;
        this.dimension = dimension;
        this.documentMaxTokens = documentMaxTokens;
        this.vectorNormalization = vectorNormalization;
        this.similarity = similarity;
        this.chunkCount = chunkCount;
        this.chunkSetHash = chunkSetHash;
        this.keywordIndexFormatVersion = keywordIndexFormatVersion;
        this.vectorIndexFormatVersion = vectorIndexFormatVersion;
    }

    public String getStrategyVersion() { return strategyVersion; }
    public String getNormalizationVersion() { return normalizationVersion; }
    public String getRetrievalPolicyVersion() { return retrievalPolicyVersion; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public String getEmbeddingArtifactSha256() { return embeddingArtifactSha256; }
    public int getDimension() { return dimension; }
    public int getDocumentMaxTokens() { return documentMaxTokens; }
    public String getVectorNormalization() { return vectorNormalization; }
    public String getSimilarity() { return similarity; }
    public int getChunkCount() { return chunkCount; }
    public String getChunkSetHash() { return chunkSetHash; }
    public String getKeywordIndexFormatVersion() { return keywordIndexFormatVersion; }
    public String getVectorIndexFormatVersion() { return vectorIndexFormatVersion; }
}
