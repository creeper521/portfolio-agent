package com.portfolio.agent.portfolio.domain;

import java.util.List;
import java.util.Objects;

public final class RuntimeRetrievalContent {

    private final RetrievalManifest manifest;
    private final List<RagDocument> documents;
    private final RuntimeKeywordIndex keywordIndex;
    private final RuntimeVectorIndex vectorIndex;

    public RuntimeRetrievalContent(
            RetrievalManifest manifest,
            List<RagDocument> documents,
            RuntimeKeywordIndex keywordIndex,
            RuntimeVectorIndex vectorIndex
    ) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.documents = List.copyOf(documents);
        this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
    }

    public RetrievalManifest getManifest() { return manifest; }
    public List<RagDocument> getDocuments() { return documents; }
    public RuntimeKeywordIndex getKeywordIndex() { return keywordIndex; }
    public RuntimeVectorIndex getVectorIndex() { return vectorIndex; }
}
