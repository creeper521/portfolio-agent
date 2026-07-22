package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.EmbeddingVector;

@FunctionalInterface
public interface LocalEmbeddingPort {
    EmbeddingVector embedQuery(String localQueryText);
}
