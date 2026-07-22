package com.portfolio.agent.portfolio.release;

@FunctionalInterface
public interface DocumentEmbeddingPort {

    float[] embedDocument(String publicDocumentText);
}
