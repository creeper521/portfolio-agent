package com.portfolio.agent.ingestion.gateway;

@FunctionalInterface
public interface DocumentEmbeddingPort {

    float[] embedDocument(String privateDocumentText);
}
