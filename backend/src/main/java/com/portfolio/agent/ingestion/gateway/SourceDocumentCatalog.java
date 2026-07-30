package com.portfolio.agent.ingestion.gateway;

import java.util.Map;
import java.util.Optional;

public interface SourceDocumentCatalog {

    Map<String, String> knownDocuments();

    Optional<String> contentHash(String relativePath);
}
