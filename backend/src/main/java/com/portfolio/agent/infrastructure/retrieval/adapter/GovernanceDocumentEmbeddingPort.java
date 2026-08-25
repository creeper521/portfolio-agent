package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 治理导入侧文档 embedding 端口：私有知识库治理导入专用的
 * {@link DocumentEmbeddingPort} 适配器。
 *
 * <p>与公开运行时的本地检索端口严格分离：本端口只在
 * {@code portfolio.database.governance.enabled=true} 的治理导入能力下装配，
 * 按需懒加载并复用同一个本地 ONNX 适配器，文档侧不拼接 BGE 查询指令。
 * profile 不是 HYBRID 或模型目录未配置时直接失败
 * （LOCAL_EMBEDDING_DISABLED / LOCAL_MODEL_DIRECTORY_REQUIRED），不回退。
 */
public final class GovernanceDocumentEmbeddingPort implements DocumentEmbeddingPort, AutoCloseable {

    private final RetrievalProperties properties;
    private final LocalEmbeddingArtifactVerifier verifier;
    private OnnxLocalEmbeddingAdapter adapter;

    /**
     * 构造端口（此时不加载模型，首次调用 embedDocument 才校验并初始化）。
     *
     * @param properties 检索配置（profile 与模型目录）
     * @param verifier 本地模型工件校验器
     */
    public GovernanceDocumentEmbeddingPort(
            RetrievalProperties properties, LocalEmbeddingArtifactVerifier verifier) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    /**
     * 对私有治理文档文本计算 embedding（本地推理，可能耗时）。
     *
     * @param privateDocumentText 治理导入管线内的文档文本，仅在本地模型消费
     * @return 归一化 embedding 的副本
     * @throws LocalEmbeddingFailureException profile 未启用、目录缺失、
     *         工件校验失败或本地推理失败
     */
    @Override
    public synchronized float[] embedDocument(String privateDocumentText) {
        return documentAdapter().embedQuery(privateDocumentText).copyValues();
    }

    /** 释放懒加载的本地适配器；可安全重复调用。 */
    @Override
    public synchronized void close() {
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }

    /**
     * 懒加载文档侧适配器：仅 HYBRID profile、已配置且通过工件哈希校验的
     * 模型目录才会创建；文档模式（forDocuments）不拼接查询指令。
     */
    private OnnxLocalEmbeddingAdapter documentAdapter() {
        if (properties.getProfile() != RetrievalProfile.HYBRID) {
            throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
        }
        if (adapter == null) {
            String configuredDirectory = properties.getModelDirectory() == null ? "" : properties.getModelDirectory().strip();
            if (configuredDirectory.isEmpty()) {
                throw new LocalEmbeddingFailureException("LOCAL_MODEL_DIRECTORY_REQUIRED");
            }
            LocalEmbeddingArtifact artifact = verifier.verify(Path.of(configuredDirectory));
            adapter = OnnxLocalEmbeddingAdapter.forDocuments(
                    Path.of(configuredDirectory), artifact.getMaxTokens(), artifact.getDimension(),
                    artifact.getIntraOpThreads(), artifact.getInterOpThreads());
        }
        return adapter;
    }
}
