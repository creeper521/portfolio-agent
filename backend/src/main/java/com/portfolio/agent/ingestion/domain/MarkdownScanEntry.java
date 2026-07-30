package com.portfolio.agent.ingestion.domain;

import java.util.Objects;

public final class MarkdownScanEntry {

    private final String relativePath;
    private final SourceDocumentStatus status;
    private final String contentHash;
    private final String errorCode;

    public MarkdownScanEntry(
            String relativePath,
            SourceDocumentStatus status,
            String contentHash,
            String errorCode) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.status = Objects.requireNonNull(status, "status");
        this.contentHash = contentHash;
        this.errorCode = errorCode;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public SourceDocumentStatus getStatus() {
        return status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
