package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import java.nio.file.Path;
import java.util.Objects;

public final class GovernanceDocumentEmbeddingPort implements DocumentEmbeddingPort, AutoCloseable {

    private final RetrievalProperties properties;
    private final LocalEmbeddingArtifactVerifier verifier;
    private OnnxLocalEmbeddingAdapter adapter;

    public GovernanceDocumentEmbeddingPort(
            RetrievalProperties properties, LocalEmbeddingArtifactVerifier verifier) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Override
    public synchronized float[] embedDocument(String privateDocumentText) {
        return documentAdapter().embedQuery(privateDocumentText).copyValues();
    }

    @Override
    public synchronized void close() {
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }

    private OnnxLocalEmbeddingAdapter documentAdapter() {
        if (properties.getProfile() != RetrievalProfile.HYBRID) {
            throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
        }
        if (adapter == null) {
            String configuredDirectory = properties.getModelDirectory() == null ? "" : properties.getModelDirectory().strip();
            if (configuredDirectory.isEmpty()) {
                throw new LocalEmbeddingFailureException("LOCAL_MODEL_DIRECTORY_REQUIRED");
            }
            LocalEmbeddingArtifact artifact = verifier.verify(Path.of(configuredDirectory));
            adapter = OnnxLocalEmbeddingAdapter.forDocuments(
                    Path.of(configuredDirectory), artifact.getMaxTokens(), artifact.getDimension(),
                    artifact.getIntraOpThreads(), artifact.getInterOpThreads());
        }
        return adapter;
    }
}
