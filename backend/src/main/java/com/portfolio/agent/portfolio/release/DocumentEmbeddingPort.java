package com.portfolio.agent.portfolio.release;

/**
 * 文档向量化端口：由本地嵌入实现提供，把公开 RAG 文档文本编码为向量。
 *
 * <p>仅在发布编译（RetrievalBundleCompiler/LocalDocumentEmbeddingBuilder）中使用，
 * 属于受隐私与配置门禁约束的本地公开检索能力，不接入外部模型服务。
 */
@FunctionalInterface
public interface DocumentEmbeddingPort {

    /**
     * 对单篇公开文档文本计算嵌入向量。
     *
     * @param publicDocumentText 已过滤的公开文档文本
     * @return 向量；实现须保证维度与配置一致、值有限且已做 L2 归一化，由调用方校验
     */
    float[] embedDocument(String publicDocumentText);
}
