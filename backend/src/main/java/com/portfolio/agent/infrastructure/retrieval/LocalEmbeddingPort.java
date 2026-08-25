package com.portfolio.agent.infrastructure.retrieval;



/**
 * 本地 embedding 端口：把一段已按 BGE 查询约定构造的本地查询文本转换为向量。
 *
 * <p>仅面向本地公开检索路径（批准隐私门下的 BGE 通道），不发起任何外部
 * 网络调用；实现方失败时抛出 {@link LocalEmbeddingFailureException}。
 */
@FunctionalInterface
public interface LocalEmbeddingPort {

    /**
     * 对本地查询文本计算 embedding。
     *
     * @param localQueryText 已由查询工厂处理的本地查询文本
     * @return 对应的 embedding 向量
     * @throws LocalEmbeddingFailureException 本地推理失败（模型缺失、输入非法等）
     */
    EmbeddingVector embedQuery(String localQueryText);
}
