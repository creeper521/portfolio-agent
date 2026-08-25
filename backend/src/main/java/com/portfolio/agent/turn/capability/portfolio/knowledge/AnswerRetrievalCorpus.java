package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地回答检索语料（不可变值对象）。
 *
 * <p>包含 BM25 关键词索引、按 chunkId 索引的向量与分块，以及嵌入模型标识、
 * 嵌入工件 SHA-256 与向量维度；向量在构造与复制时均做数组防御性拷贝，
 * 防止调用方修改内部状态。
 */
public final class AnswerRetrievalCorpus {

    private final AnswerKeywordIndex keywordIndex;
    private final Map<String, float[]> vectors;
    private final Map<String, AnswerRetrievalChunk> chunks;
    private final String embeddingModelId;
    private final String embeddingArtifactSha256;
    private final int dimension;

    public AnswerRetrievalCorpus(
            AnswerKeywordIndex keywordIndex,
            Map<String, float[]> vectors,
            Map<String, AnswerRetrievalChunk> chunks
    ) {
        this(keywordIndex, vectors, chunks, null, null, 0);
    }

    public AnswerRetrievalCorpus(
            AnswerKeywordIndex keywordIndex,
            Map<String, float[]> vectors,
            Map<String, AnswerRetrievalChunk> chunks,
            String embeddingModelId,
            String embeddingArtifactSha256,
            int dimension
    ) {
        this.keywordIndex = keywordIndex;
        Map<String, float[]> vectorCopies = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            vectorCopies.put(
                    entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.vectors = Collections.unmodifiableMap(vectorCopies);
        this.chunks = Collections.unmodifiableMap(new LinkedHashMap<>(chunks));
        this.embeddingModelId = embeddingModelId;
        this.embeddingArtifactSha256 = embeddingArtifactSha256;
        this.dimension = dimension;
    }

    public AnswerKeywordIndex getKeywordIndex() { return keywordIndex; }

    /** 返回向量的深拷贝（逐向量复制数组），调用方可安全修改副本而不影响内部状态。 */
    public Map<String, float[]> copyVectors() {
        Map<String, float[]> copies = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            copies.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copies);
    }

    public Map<String, AnswerRetrievalChunk> getChunks() { return chunks; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public String getEmbeddingArtifactSha256() { return embeddingArtifactSha256; }
    public int getDimension() { return dimension; }
}
