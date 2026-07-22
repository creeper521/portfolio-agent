package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RetrievalManifest;

public final class RetrievalCompilation {

    private final byte[] ragDocuments;
    private final byte[] keywordIndex;
    private final byte[] vectorIndex;
    private final RetrievalManifest manifest;

    public RetrievalCompilation(
            byte[] ragDocuments,
            byte[] keywordIndex,
            byte[] vectorIndex,
            RetrievalManifest manifest
    ) {
        this.ragDocuments = ragDocuments.clone();
        this.keywordIndex = keywordIndex.clone();
        this.vectorIndex = vectorIndex.clone();
        this.manifest = manifest;
    }

    public byte[] getRagDocuments() { return ragDocuments.clone(); }
    public byte[] getKeywordIndex() { return keywordIndex.clone(); }
    public byte[] getVectorIndex() { return vectorIndex.clone(); }
    public RetrievalManifest getManifest() { return manifest; }
}
