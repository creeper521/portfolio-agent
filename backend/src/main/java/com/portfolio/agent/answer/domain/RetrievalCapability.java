package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class RetrievalCapability {

    private final boolean groundedQuestionsEnabled;
    private final RetrievalMode mode;
    private final String embeddingModelId;
    private final String embeddingArtifactSha256;
    private final int dimension;

    private RetrievalCapability(boolean groundedQuestionsEnabled, RetrievalMode mode,
            String embeddingModelId, String embeddingArtifactSha256, int dimension) {
        this.groundedQuestionsEnabled = groundedQuestionsEnabled;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.embeddingModelId = embeddingModelId;
        this.embeddingArtifactSha256 = embeddingArtifactSha256;
        this.dimension = dimension;
    }

    public static RetrievalCapability disabled() {
        return new RetrievalCapability(false, RetrievalMode.KEYWORD_ONLY, null, null, 0);
    }

    public static RetrievalCapability keywordOnly() {
        return new RetrievalCapability(true, RetrievalMode.KEYWORD_ONLY, null, null, 0);
    }

    public static RetrievalCapability hybridEnabled(
            String embeddingModelId,
            String embeddingArtifactSha256,
            int dimension
    ) {
        if (embeddingModelId == null || embeddingModelId.isBlank()
                || embeddingArtifactSha256 == null || embeddingArtifactSha256.isBlank()
                || dimension <= 0) {
            throw new IllegalArgumentException("hybrid embedding identity is required");
        }
        return new RetrievalCapability(true, RetrievalMode.HYBRID_ENABLED,
                embeddingModelId, embeddingArtifactSha256, dimension);
    }

    public boolean isGroundedQuestionsEnabled() {
        return groundedQuestionsEnabled;
    }

    public RetrievalMode getMode() {
        return mode;
    }

    public boolean supports(AnswerRetrievalCorpus corpus) {
        if (!groundedQuestionsEnabled || corpus == null) {
            return false;
        }
        if (mode == RetrievalMode.KEYWORD_ONLY) {
            return true;
        }
        return embeddingModelId.equals(corpus.getEmbeddingModelId())
                && embeddingArtifactSha256.equals(corpus.getEmbeddingArtifactSha256())
                && dimension == corpus.getDimension();
    }
}
