package com.portfolio.agent.ingestion.gateway;

import com.portfolio.agent.ingestion.domain.ImportedMarkdownDocument;
import java.util.Map;
import java.util.Set;

public interface MarkdownGovernanceStore extends SourceDocumentCatalog {

    Set<String> pendingDocuments();

    Map<String, float[]> reusableEmbeddings(String relativePath, Set<String> chunkHashes);

    void saveRevision(ImportedMarkdownDocument document);

    void markMissing(String relativePath);
}
