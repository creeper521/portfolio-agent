package com.portfolio.agent.ingestion.domain;

public enum SourceDocumentStatus {
    ADDED,
    CHANGED,
    UNCHANGED,
    MISSING,
    FAILED,
    BLOCKED
}
