package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过本地嵌入端口为 RAG 文档生成向量的构建器。
 *
 * <p>按 chunkId 顺序逐篇调用 {@link DocumentEmbeddingPort}，并对每个向量做严格校验
 * （维度一致、值有限、L2 范数接近 1），防止异常嵌入进入发布包。
 * 产出为不可变 Map（chunkId → 向量副本）。
 *
 * <p>失败行为：文档为 null、向量维度不符、值非有限、未归一化或 chunkId 重复时，
 * 抛出 {@link InvalidPortfolioSnapshotException}；配置非法时构造器抛出
 * {@link IllegalArgumentException}。
 */
public final class LocalDocumentEmbeddingBuilder {

    private final DocumentEmbeddingPort embeddingPort;
    private final int dimension;

    public LocalDocumentEmbeddingBuilder(DocumentEmbeddingPort embeddingPort, int dimension) {
        if (embeddingPort == null || dimension <= 0) {
            throw new IllegalArgumentException("document embedding configuration is invalid");
        }
        this.embeddingPort = embeddingPort;
        this.dimension = dimension;
    }

    /**
     * 为文档集合计算嵌入向量。
     *
     * <p>按 chunkId 排序处理以保证确定性；返回的 Map 不可变，向量均为端口返回值的副本，
     * 避免调用方与内部状态共享数组。
     *
     * @param documents RAG 文档列表
     * @return chunkId 到向量的不可变映射
     * @throws InvalidPortfolioSnapshotException 文档为 null、向量校验失败或 chunkId 重复
     */
    public Map<String, float[]> build(List<RagDocument> documents) {
        if (documents == null) {
            throw new InvalidPortfolioSnapshotException("RAG documents are required");
        }
        Map<String, float[]> vectors = new LinkedHashMap<>();
        for (RagDocument document : documents.stream()
                .sorted(java.util.Comparator.comparing(RagDocument::getChunkId))
                .toList()) {
            float[] vector = embeddingPort.embedDocument(document.getText());
            validate(vector);
            if (vectors.put(document.getChunkId(), vector.clone()) != null) {
                throw new InvalidPortfolioSnapshotException(
                        "duplicate document vector chunkId: " + document.getChunkId());
            }
        }
        return java.util.Collections.unmodifiableMap(vectors);
    }

    /**
     * 校验单个嵌入向量：维度与配置一致、所有值有限、L2 范数与 1 的偏差不超过 0.001。
     *
     * @throws InvalidPortfolioSnapshotException 任一条件不满足
     */
    private void validate(float[] vector) {
        if (vector == null || vector.length != dimension) {
            throw new InvalidPortfolioSnapshotException("document vector dimension mismatch");
        }
        double squaredNorm = 0.0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new InvalidPortfolioSnapshotException(
                        "document vector values must be finite");
            }
            squaredNorm += value * value;
        }
        if (Math.abs(Math.sqrt(squaredNorm) - 1.0) > 0.001) {
            throw new InvalidPortfolioSnapshotException(
                    "document vector must be L2 normalized");
        }
    }
}
