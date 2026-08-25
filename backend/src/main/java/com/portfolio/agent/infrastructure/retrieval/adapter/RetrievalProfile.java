package com.portfolio.agent.infrastructure.retrieval.adapter;

/** 检索 profile：DISABLED（缺省，检索关闭）、KEYWORD_ONLY（仅关键词）、
 * HYBRID（关键词 + 本地 BGE 向量检索，需通过隐私与配置门）。 */
public enum RetrievalProfile {
    DISABLED,
    KEYWORD_ONLY,
    HYBRID
}
