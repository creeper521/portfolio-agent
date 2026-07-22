package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfiguration {

    @Bean
    RetrievalPolicy retrievalPolicy() {
        return RetrievalPolicy.firstRelease();
    }

    @Bean
    RetrievalCapability retrievalCapability(RetrievalProperties properties) {
        return switch (properties.getProfile()) {
            case DISABLED -> RetrievalCapability.disabled();
            case KEYWORD_ONLY -> RetrievalCapability.keywordOnly();
            case HYBRID -> {
                LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                        .descriptor();
                yield RetrievalCapability.hybridEnabled(
                        artifact.getModelId(), artifact.getDescriptorSha256(),
                        artifact.getDimension());
            }
        };
    }

    @Bean
    LocalEmbeddingPort localEmbeddingPort(RetrievalProperties properties) {
        if (properties.getProfile() != RetrievalProfile.HYBRID) {
            return localText -> {
                throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
            };
        }
        String configuredDirectory = properties.getModelDirectory() == null
                ? ""
                : properties.getModelDirectory().strip();
        if (configuredDirectory.isEmpty()) {
            throw new LocalEmbeddingFailureException("LOCAL_MODEL_DIRECTORY_REQUIRED");
        }
        Path modelDirectory = Path.of(configuredDirectory);
        LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                .verify(modelDirectory);
        return new OnnxLocalEmbeddingAdapter(
                modelDirectory,
                artifact.getQueryInstruction(),
                artifact.getMaxTokens(),
                artifact.getDimension(),
                artifact.getIntraOpThreads(),
                artifact.getInterOpThreads());
    }

    @Bean
    LocalRetrievalCoordinator localRetrievalCoordinator(LocalEmbeddingPort embeddingPort) {
        return new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(),
                new KeywordRetriever(),
                new VectorRetriever(),
                new ReciprocalRankFusion(),
                new RetrievalContextValidator(),
                embeddingPort);
    }
}
