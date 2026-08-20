package com.portfolio.agent.infrastructure.retrieval;



@FunctionalInterface
public interface LocalEmbeddingPort {
    EmbeddingVector embedQuery(String localQueryText);
}
