package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.NormalizedRetrievalQuery;
import com.portfolio.agent.answer.service.RankedRetrievalHit;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Retrieves from every published chunk. Expected subjects are deliberately absent from this API.
 */
public final class FullCorpusRetrievalExecutor implements EvalExecutor {

    private final RuntimeContentSnapshot snapshot;
    private final RetrievalPolicy policy;
    private final RetrievalQueryNormalizer normalizer;
    private final KeywordRetriever keywordRetriever;
    private final VectorRetriever vectorRetriever;
    private final ReciprocalRankFusion fusion;
    private final RetrievalContextValidator contextValidator;
    private final LocalEmbeddingPort embeddingPort;
    private final RetrievalObservationFactory observationFactory;

    public FullCorpusRetrievalExecutor(
            RuntimeContentSnapshot snapshot,
            RetrievalPolicy policy,
            RetrievalQueryNormalizer normalizer,
            KeywordRetriever keywordRetriever,
            VectorRetriever vectorRetriever,
            ReciprocalRankFusion fusion,
            RetrievalContextValidator contextValidator,
            LocalEmbeddingPort embeddingPort
    ) {
        this(snapshot, policy, normalizer, keywordRetriever, vectorRetriever, fusion,
                contextValidator, embeddingPort, new RetrievalObservationFactory());
    }

    FullCorpusRetrievalExecutor(
            RuntimeContentSnapshot snapshot,
            RetrievalPolicy policy,
            RetrievalQueryNormalizer normalizer,
            KeywordRetriever keywordRetriever,
            VectorRetriever vectorRetriever,
            ReciprocalRankFusion fusion,
            RetrievalContextValidator contextValidator,
            LocalEmbeddingPort embeddingPort,
            RetrievalObservationFactory observationFactory
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.keywordRetriever = Objects.requireNonNull(keywordRetriever, "keywordRetriever");
        this.vectorRetriever = Objects.requireNonNull(vectorRetriever, "vectorRetriever");
        this.fusion = Objects.requireNonNull(fusion, "fusion");
        this.contextValidator = Objects.requireNonNull(contextValidator, "contextValidator");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.observationFactory = Objects.requireNonNull(observationFactory, "observationFactory");
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.FULL_CORPUS_RETRIEVAL;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        if (!supports(input.getLayer())) {
            throw new IllegalArgumentException("Unsupported evaluation layer: " + input.getLayer());
        }
        long startedAt = System.nanoTime();
        RuntimeAnswerContent content = new LocalPortfolioKnowledgeAdapter(() -> snapshot).getContent();
        AnswerRetrievalCorpus corpus = content.getRetrievalCorpus().orElseThrow(
                () -> new IllegalArgumentException("full-corpus retrieval requires published retrieval content"));
        Set<String> allowedChunkIds = Set.copyOf(corpus.getChunks().keySet());
        NormalizedRetrievalQuery query = normalizer.normalize(lastUserMessage(input.getMessages()));
        List<RankedRetrievalHit> keywordHits = keywordRetriever.retrieve(
                corpus.getKeywordIndex(), query.getTerms(), allowedChunkIds, policy.getKeywordTopK());
        EmbeddingVector queryVector = embeddingPort.embedQuery(query.getLocalText());
        List<RankedRetrievalHit> vectorHits = vectorRetriever.retrieve(
                queryVector, corpus.copyVectors(), allowedChunkIds, policy.getVectorTopK(),
                policy.getVectorCandidateThreshold());
        List<RetrievalCandidate> fusedCandidates = fusion.fuse(
                keywordHits, vectorHits, policy.getRrfK());
        RetrievalDecision decision = contextValidator.validate(
                query, allClaims(content), allEvidence(content), corpus.getChunks(), fusedCandidates,
                RetrievalMode.HYBRID_ENABLED, policy);
        long durationMilliseconds = (System.nanoTime() - startedAt) / 1_000_000L;
        return observationFactory.fullCorpus(input, decision, corpus.getChunks(), keywordHits,
                vectorHits, fusedCandidates, durationMilliseconds);
    }

    private String lastUserMessage(List<EvalMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            EvalMessage message = messages.get(index);
            if ("user".equals(message.getRole())) {
                return message.getContent();
            }
        }
        throw new IllegalArgumentException("full-corpus retrieval requires one user message");
    }

    private List<AnswerClaimProjection> allClaims(RuntimeAnswerContent content) {
        List<AnswerClaimProjection> claims = new ArrayList<AnswerClaimProjection>();
        for (AnswerKnowledge subject : allSubjects(content)) {
            claims.addAll(subject.getClaims());
        }
        return List.copyOf(claims);
    }

    private List<AnswerEvidence> allEvidence(RuntimeAnswerContent content) {
        List<AnswerEvidence> evidence = new ArrayList<AnswerEvidence>();
        for (AnswerKnowledge subject : allSubjects(content)) {
            evidence.addAll(subject.getEvidence());
        }
        return List.copyOf(evidence);
    }

    private List<AnswerKnowledge> allSubjects(RuntimeAnswerContent content) {
        List<AnswerKnowledge> subjects = new ArrayList<AnswerKnowledge>();
        subjects.addAll(content.getProjects());
        subjects.addAll(content.getCases());
        return List.copyOf(subjects);
    }
}
