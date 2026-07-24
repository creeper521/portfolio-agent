package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
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
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetrievalComparisonRunner {

    private final RetrievalQueryNormalizer normalizer;
    private final KeywordRetriever keywordRetriever;
    private final VectorRetriever vectorRetriever;
    private final ReciprocalRankFusion fusion;
    private final RetrievalContextValidator contextValidator;
    private final LocalEmbeddingPort embeddingPort;

    public RetrievalComparisonRunner(
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

    public List<RetrievalRouteEvaluation> run(
            RetrievalBenchmarkSuite suite,
            RuntimeContentSnapshot snapshot,
            AnswerRetrievalCorpus corpus,
            RetrievalPolicy policy
    ) {
        RuntimeAnswerContent content = new LocalPortfolioKnowledgeAdapter(() -> snapshot)
                .getContent();
        List<RetrievalRouteEvaluation> evaluations = new ArrayList<>();
        for (RetrievalBenchmarkCase item : suite.getCases()) {
            AnswerKnowledge subject = findSubject(content, item);
            Map<String, AnswerRetrievalChunk> subjectChunks =
                    subjectChunks(corpus, item);
            Set<String> allowedChunkIds = subjectChunks.keySet();
            NormalizedRetrievalQuery query = normalizer.normalize(item.getQuery());
            EmbeddingVector queryVector = embeddingPort.embedQuery(query.getLocalText());
            List<RankedRetrievalHit> keywordHits = keywordRetriever.retrieve(
                    corpus.getKeywordIndex(),
                    query.getTerms(),
                    allowedChunkIds,
                    policy.getKeywordTopK()
            );
            List<RankedRetrievalHit> vectorHits = vectorRetriever.retrieve(
                    queryVector,
                    corpus.copyVectors(),
                    allowedChunkIds,
                    policy.getVectorTopK(),
                    policy.getVectorCandidateThreshold()
            );
            List<RetrievalCandidate> keywordCandidates = fusion.fuse(
                    keywordHits, List.of(), policy.getRrfK());
            List<RetrievalCandidate> vectorCandidates = fusion.fuse(
                    List.of(), vectorHits, policy.getRrfK());
            List<RetrievalCandidate> hybridCandidates = fusion.fuse(
                    keywordHits, vectorHits, policy.getRrfK());
            evaluations.add(evaluate(
                    RetrievalBenchmarkRoute.KEYWORD,
                    RetrievalMode.KEYWORD_ONLY,
                    item,
                    subject,
                    subjectChunks,
                    keywordCandidates,
                    policy
            ));
            evaluations.add(evaluate(
                    RetrievalBenchmarkRoute.VECTOR,
                    RetrievalMode.HYBRID_ENABLED,
                    item,
                    subject,
                    subjectChunks,
                    vectorCandidates,
                    policy
            ));
            evaluations.add(evaluate(
                    RetrievalBenchmarkRoute.HYBRID,
                    RetrievalMode.HYBRID_ENABLED,
                    item,
                    subject,
                    subjectChunks,
                    hybridCandidates,
                    policy
            ));
        }
        return List.copyOf(evaluations);
    }

    private AnswerKnowledge findSubject(
            RuntimeAnswerContent content,
            RetrievalBenchmarkCase item
    ) {
        List<AnswerKnowledge> subjects;
        if (item.getSubjectType() == ClaimSubjectType.PROJECT) {
            subjects = content.getProjects();
        } else if (item.getSubjectType() == ClaimSubjectType.CASE) {
            subjects = content.getCases();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported benchmark subject type: " + item.getSubjectType());
        }
        return subjects.stream()
                .filter(subject -> subject.getSlug().equals(item.getSubjectSlug()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown benchmark subject: " + item.getSubjectSlug()));
    }

    private Map<String, AnswerRetrievalChunk> subjectChunks(
            AnswerRetrievalCorpus corpus,
            RetrievalBenchmarkCase item
    ) {
        Map<String, AnswerRetrievalChunk> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, AnswerRetrievalChunk> entry
                : corpus.getChunks().entrySet()) {
            AnswerRetrievalChunk chunk = entry.getValue();
            boolean allowed = item.getSubjectType() == ClaimSubjectType.PROJECT
                    ? chunk.getProjectSlugs().contains(item.getSubjectSlug())
                    : chunk.getCaseSlugs().contains(item.getSubjectSlug());
            if (allowed) {
                filtered.put(entry.getKey(), chunk);
            }
        }
        return java.util.Collections.unmodifiableMap(filtered);
    }

    private RetrievalRouteEvaluation evaluate(
            RetrievalBenchmarkRoute route,
            RetrievalMode mode,
            RetrievalBenchmarkCase item,
            AnswerKnowledge subject,
            Map<String, AnswerRetrievalChunk> subjectChunks,
            List<RetrievalCandidate> candidates,
            RetrievalPolicy policy
    ) {
        Integer expectedRank = expectedRank(item, subjectChunks, candidates);
        RetrievalDecision decision = contextValidator.validate(
                subject.getClaims(),
                subject.getEvidence(),
                subjectChunks,
                candidates,
                mode,
                policy
        );
        return new RetrievalRouteEvaluation(
                route,
                item.getCaseId(),
                item.getSplit(),
                item.getCategory(),
                item.getExpectedDecision(),
                decision.getType(),
                expectedRank,
                decision.getSelectedClaimIds(),
                decision.getSelectedChunkIds()
        );
    }

    private Integer expectedRank(
            RetrievalBenchmarkCase item,
            Map<String, AnswerRetrievalChunk> chunks,
            List<RetrievalCandidate> candidates
    ) {
        for (int index = 0; index < candidates.size(); index++) {
            RetrievalCandidate candidate = candidates.get(index);
            if (item.getExpectedChunkIds().contains(candidate.getChunkId())) {
                return index + 1;
            }
            AnswerRetrievalChunk chunk = chunks.get(candidate.getChunkId());
            if (chunk != null
                    && chunk.getClaimIds().stream()
                    .anyMatch(item.getExpectedClaimIds()::contains)) {
                return index + 1;
            }
        }
        return null;
    }
}
