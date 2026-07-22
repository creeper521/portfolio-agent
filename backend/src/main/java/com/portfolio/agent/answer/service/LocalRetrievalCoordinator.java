package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class LocalRetrievalCoordinator {

    private final RetrievalQueryNormalizer normalizer;
    private final KeywordRetriever keywordRetriever;
    private final VectorRetriever vectorRetriever;
    private final ReciprocalRankFusion fusion;
    private final RetrievalContextValidator contextValidator;
    private final LocalEmbeddingPort embeddingPort;

    public LocalRetrievalCoordinator(
            RetrievalQueryNormalizer normalizer,
            KeywordRetriever keywordRetriever,
            VectorRetriever vectorRetriever,
            ReciprocalRankFusion fusion,
            RetrievalContextValidator contextValidator,
            LocalEmbeddingPort embeddingPort
    ) {
        this.normalizer = normalizer;
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
        this.fusion = fusion;
        this.contextValidator = contextValidator;
        this.embeddingPort = embeddingPort;
    }

    public RetrievalDecision retrieve(
            String localQueryText,
            String projectSlug,
            AnswerRetrievalCorpus corpus,
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            RetrievalMode requestedMode,
            RetrievalPolicy policy
    ) {
        NormalizedRetrievalQuery query = normalizer.normalize(localQueryText);
        Map<String, com.portfolio.agent.answer.domain.AnswerRetrievalChunk> projectChunks =
                corpus.getChunks().entrySet().stream()
                        .filter(entry -> entry.getValue().getProjectSlugs().contains(projectSlug))
                        .collect(Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue));
        Set<String> allowedChunkIds = projectChunks.keySet();
        List<RankedRetrievalHit> keywordHits = keywordRetriever.retrieve(
                corpus.getKeywordIndex(), query.getTerms(), allowedChunkIds,
                policy.getKeywordTopK());
        RetrievalMode actualMode = requestedMode;
        List<RankedRetrievalHit> vectorHits = List.of();
        if (requestedMode == RetrievalMode.HYBRID_ENABLED) {
            try {
                EmbeddingVector queryVector = embeddingPort.embedQuery(query.getLocalText());
                vectorHits = vectorRetriever.retrieve(
                        queryVector, corpus.copyVectors(), allowedChunkIds,
                        policy.getVectorTopK(),
                        policy.getVectorCandidateThreshold());
            } catch (LocalEmbeddingFailureException exception) {
                actualMode = RetrievalMode.KEYWORD_FALLBACK;
            }
        }
        List<RetrievalCandidate> candidates = fusion.fuse(
                keywordHits, vectorHits, policy.getRrfK());
        return contextValidator.validate(
                claims, evidence, projectChunks, candidates, actualMode, policy);
    }
}
