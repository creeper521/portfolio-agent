package com.portfolio.agent.infrastructure.retrieval.adapter;

/**
 * 本地 embedding 工件元数据：从打包描述符解析出的模型参数
 * （模型 ID、描述符哈希、维度、token 上限、查询指令与线程配置）。
 *
 * <p>纯不可变数据载体，由 {@link LocalEmbeddingArtifactVerifier} 构造，
 * 供 ONNX 适配器初始化与启动诊断消费。
 */
public final class LocalEmbeddingArtifact {
    private final String modelId;
    private final String descriptorSha256;
    private final int dimension;
    private final int maxTokens;
    private final String queryInstruction;
    private final int intraOpThreads;
    private final int interOpThreads;

    LocalEmbeddingArtifact(String modelId, String descriptorSha256, int dimension,
            int maxTokens, String queryInstruction, int intraOpThreads, int interOpThreads) {
        this.modelId = modelId;
        this.descriptorSha256 = descriptorSha256;
        this.dimension = dimension;
        this.maxTokens = maxTokens;
        this.queryInstruction = queryInstruction;
        this.intraOpThreads = intraOpThreads;
        this.interOpThreads = interOpThreads;
    }

    public String getModelId() { return modelId; }
    public String getDescriptorSha256() { return descriptorSha256; }
    public int getDimension() { return dimension; }
    public int getMaxTokens() { return maxTokens; }
    public String getQueryInstruction() { return queryInstruction; }
    public int getIntraOpThreads() { return intraOpThreads; }
    public int getInterOpThreads() { return interOpThreads; }
}
