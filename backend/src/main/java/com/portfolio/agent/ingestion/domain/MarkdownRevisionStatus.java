package com.portfolio.agent.ingestion.domain;

public enum MarkdownRevisionStatus {
    PARSED,
    VECTOR_PENDING;

    public boolean isReadyForCurrentRevision() {
        return this == PARSED;
    }
}
