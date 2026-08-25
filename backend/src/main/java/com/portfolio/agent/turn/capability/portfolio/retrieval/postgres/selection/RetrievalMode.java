package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

/** 实际执行的检索模式：HYBRID 为全文+向量混合，FTS_ONLY 为向量不可用时降级到全文，UNAVAILABLE 为两者皆不可用。 */
public enum RetrievalMode {
    HYBRID,
    FTS_ONLY,
    UNAVAILABLE
}
