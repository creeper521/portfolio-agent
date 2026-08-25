package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;

import java.util.Objects;

/** 一次检索请求（不可变值对象）：目标语料后端与检索策略的组合。 */
public final class RetrievalRequest {
    private final CorpusBackend backend;
    private final SearchStrategy strategy;

    public RetrievalRequest(CorpusBackend backend, SearchStrategy strategy) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }
    public CorpusBackend getBackend() { return backend; }
    public SearchStrategy getStrategy() { return strategy; }
}
