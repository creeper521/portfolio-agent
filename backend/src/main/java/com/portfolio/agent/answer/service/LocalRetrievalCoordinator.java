package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class LocalRetrievalCoordinator {

    private final RetrievalQueryNormalizer normalizer;
    private final KeywordRetriever keywordRetriever;
    private final VectorRetriever vectorRetriever;
    private final ReciprocalRankFusion fusion;
    private final RetrievalContextValidator contextValidator;
    private final LocalEmbeddingPort embeddingPort;
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public LocalRetrievalCoordinator(
            RetrievalQueryNormalizer normalizer,
            KeywordRetriever keywordRetriever,
            VectorRetriever vectorRetriever,
            ReciprocalRankFusion fusion,
            RetrievalContextValidator contextValidator,
            LocalEmbeddingPort embeddingPort
    ) {
        this(normalizer, keywordRetriever, vectorRetriever, fusion, contextValidator,
                embeddingPort, event -> { });
    }

    public LocalRetrievalCoordinator(
            RetrievalQueryNormalizer normalizer,
            KeywordRetriever keywordRetriever,
            VectorRetriever vectorRetriever,
            ReciprocalRankFusion fusion,
            RetrievalContextValidator contextValidator,
            LocalEmbeddingPort embeddingPort,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.normalizer = normalizer;
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
        this.fusion = fusion;
        this.contextValidator = contextValidator;
        this.embeddingPort = embeddingPort;
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher,
                "diagnosticEventPublisher");
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
        return retrieve(localQueryText, projectSlug, AnswerSubjectType.PROJECT,
                corpus, claims, evidence, requestedMode, policy);
    }

    public RetrievalDecision retrieve(
            String localQueryText,
            String subjectSlug,
            AnswerSubjectType subjectType,
            AnswerRetrievalCorpus corpus,
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            RetrievalMode requestedMode,
            RetrievalPolicy policy
    ) {
        long startedAt = System.nanoTime();
        NormalizedRetrievalQuery query = normalizer.normalize(localQueryText);
        Map<String, com.portfolio.agent.answer.domain.AnswerRetrievalChunk> projectChunks =
                corpus.getChunks().entrySet().stream()
                        .filter(entry -> subjectType == AnswerSubjectType.PROJECT
                                ? entry.getValue().getProjectSlugs().contains(subjectSlug)
                                : entry.getValue().getCaseSlugs().contains(subjectSlug))
                        .collect(Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue));
        Set<String> allowedChunkIds = projectChunks.keySet();
        List<RankedRetrievalHit> keywordHits = keywordRetriever.retrieve(
                corpus.getKeywordIndex(), query.getTerms(), allowedChunkIds,
                policy.getKeywordTopK());
        RetrievalMode actualMode = requestedMode;
        RetrievalFailureCode failureCode = null;
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
                failureCode = exception.getCode();
            }
        }
        List<RetrievalCandidate> candidates = fusion.fuse(
                keywordHits, vectorHits, policy.getRrfK());
        RetrievalDecision decision = contextValidator.validate(
                query, claims, evidence, projectChunks, candidates, actualMode, policy);
        publishRetrievalEvent(
                requestedMode,
                actualMode,
                decision,
                keywordHits.size(),
                vectorHits.size(),
                candidates.size(),
                failureCode,
                startedAt);
        return decision;
    }

    private void publishRetrievalEvent(
            RetrievalMode requestedMode,
            RetrievalMode actualMode,
            RetrievalDecision decision,
            int keywordHitCount,
            int vectorHitCount,
            int fusedCandidateCount,
            RetrievalFailureCode failureCode,
            long startedAt
    ) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(
                        failureCode == null
                                ? "retrieval.completed"
                                : "retrieval.fallback",
                        failureCode == null
                                ? DiagnosticLevel.DEBUG
                                : DiagnosticLevel.WARN)
                .field("retrieval.requested_mode", requestedMode)
                .field("retrieval.actual_mode", actualMode)
                .field("retrieval.decision", decision.getType())
                .field("retrieval.keyword_hit_count", keywordHitCount)
                .field("retrieval.vector_hit_count", vectorHitCount)
                .field("retrieval.fused_candidate_count", fusedCandidateCount)
                .field("retrieval.accepted_chunk_count",
                        decision.getSelectedChunkIds().size())
                .field("duration.bucket", DurationBuckets.fromElapsedMillis(
                        (System.nanoTime() - startedAt) / 1_000_000L));
        if (failureCode != null) {
            builder.field("failure.code", failureCode.code());
        }
        publishBestEffort(builder.build());
    }

    private void publishBestEffort(DiagnosticEvent event) {
        try {
            diagnosticEventPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Diagnostics must never change the retrieval decision.
        }
    }
}
