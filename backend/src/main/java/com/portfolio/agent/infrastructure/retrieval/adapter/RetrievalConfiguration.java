package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfiguration {

    @Bean(name = "governanceDocumentEmbeddingPort")
    @ConditionalOnProperty(prefix = "portfolio.database.governance", name = "enabled", havingValue = "true")
    DocumentEmbeddingPort governanceDocumentEmbeddingPort(RetrievalProperties properties) {
        return new GovernanceDocumentEmbeddingPort(properties, new LocalEmbeddingArtifactVerifier());
    }

    @Bean
    LocalEmbeddingPort localEmbeddingPort(
            RetrievalProperties properties,
            ApplicationStartupDiagnostics startupDiagnostics
    ) {
        if (properties.getProfile() != RetrievalProfile.HYBRID) {
            return localText -> {
                throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
            };
        }
        long startedAt = System.nanoTime();
        try {
            String configuredDirectory = properties.getModelDirectory() == null
                    ? ""
                    : properties.getModelDirectory().strip();
            if (configuredDirectory.isEmpty()) {
                throw new LocalEmbeddingFailureException("LOCAL_MODEL_DIRECTORY_REQUIRED");
            }
            Path modelDirectory = Path.of(configuredDirectory);
            LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                    .verify(modelDirectory);
            LocalEmbeddingPort embeddingPort = new OnnxLocalEmbeddingAdapter(
                    modelDirectory,
                    artifact.getQueryInstruction(),
                    artifact.getMaxTokens(),
                    artifact.getDimension(),
                    artifact.getIntraOpThreads(),
                    artifact.getInterOpThreads());
            startupDiagnostics.embeddingModelLoaded(
                    artifact.getDimension(),
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
            return embeddingPort;
        } catch (RuntimeException exception) {
            startupDiagnostics.embeddingModelFailed();
            throw exception;
        }
    }

}
