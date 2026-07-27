package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
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
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;

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
            RetrievalPolicy policy
    ) {
        RuntimeRetrievalContent retrieval = snapshot.getRetrievalContent()
                .orElseThrow(() -> new IllegalArgumentException(
                        "retrieval comparison requires published retrieval content"));
        AnswerRetrievalCorpus corpus = corpus(retrieval);
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
                    query,
                    item,
                    subject,
                    subjectChunks,
                    keywordCandidates,
                    policy
            ));
            evaluations.add(evaluate(
                    RetrievalBenchmarkRoute.VECTOR,
                    RetrievalMode.HYBRID_ENABLED,
                    query,
                    item,
                    subject,
                    subjectChunks,
                    vectorCandidates,
                    policy
            ));
            evaluations.add(evaluate(
                    RetrievalBenchmarkRoute.HYBRID,
                    RetrievalMode.HYBRID_ENABLED,
                    query,
                    item,
                    subject,
                    subjectChunks,
                    hybridCandidates,
                    policy
            ));
        }
        return List.copyOf(evaluations);
    }

    private AnswerRetrievalCorpus corpus(RuntimeRetrievalContent retrieval) {
        RuntimeKeywordIndex publishedKeyword = retrieval.getKeywordIndex();
        List<AnswerKeywordIndex.DocumentEntry> keywordDocuments = new ArrayList<>();
        for (RuntimeKeywordIndex.DocumentEntry document
                : publishedKeyword.getDocuments()) {
            keywordDocuments.add(new AnswerKeywordIndex.DocumentEntry(
                    document.getChunkId(),
                    document.getDocumentLength(),
                    document.getTermFrequencies()
            ));
        }
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                publishedKeyword.getDocumentCount(),
                publishedKeyword.getAverageDocumentLength(),
                keywordDocuments,
                publishedKeyword.getDocumentFrequencies()
        );
        Map<String, AnswerRetrievalChunk> chunks = new LinkedHashMap<>();
        for (RagDocument document : retrieval.getDocuments()) {
            chunks.put(document.getChunkId(), new AnswerRetrievalChunk(
                    document.getChunkId(),
                    document.getProjectSlugs(),
                    document.getCaseSlugs(),
                    document.getClaimIds(),
                    document.getTopics(),
                    document.getText().length()
            ));
        }
        return new AnswerRetrievalCorpus(
                keywordIndex,
                retrieval.getVectorIndex().getVectors(),
                chunks,
                retrieval.getManifest().getEmbeddingModelId(),
                retrieval.getManifest().getEmbeddingArtifactSha256(),
                retrieval.getManifest().getDimension()
        );
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
            NormalizedRetrievalQuery query,
            RetrievalBenchmarkCase item,
            AnswerKnowledge subject,
            Map<String, AnswerRetrievalChunk> subjectChunks,
            List<RetrievalCandidate> candidates,
            RetrievalPolicy policy
    ) {
        List<RetrievalExpectedRank> expectedClaimRanks =
                expectedClaimRanks(item, subjectChunks, candidates);
        List<RetrievalExpectedRank> expectedChunkRanks =
                expectedChunkRanks(item, candidates);
        Integer expectedRank = bestRank(
                expectedClaimRanks, expectedChunkRanks);
        RetrievalDecision decision = contextValidator.validate(
                query,
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
                item.getSubjectType(),
                item.getSubjectSlug(),
                item.getExpectedDecision(),
                decision.getType(),
                expectedRank,
                expectedClaimRanks,
                expectedChunkRanks,
                decision.getSelectedClaimIds(),
                decision.getSelectedChunkIds()
        );
    }

    private List<RetrievalExpectedRank> expectedClaimRanks(
            RetrievalBenchmarkCase item,
            Map<String, AnswerRetrievalChunk> chunks,
            List<RetrievalCandidate> candidates
    ) {
        List<RetrievalExpectedRank> result = new ArrayList<>();
        for (String claimId : item.getExpectedClaimIds()) {
            Integer rank = null;
            for (int index = 0; index < candidates.size(); index++) {
                AnswerRetrievalChunk chunk =
                        chunks.get(candidates.get(index).getChunkId());
                if (chunk != null && chunk.getClaimIds().contains(claimId)) {
                    rank = index + 1;
                    break;
                }
            }
            result.add(new RetrievalExpectedRank("CLAIM", claimId, rank));
        }
        return List.copyOf(result);
    }

    private List<RetrievalExpectedRank> expectedChunkRanks(
            RetrievalBenchmarkCase item,
            List<RetrievalCandidate> candidates
    ) {
        List<RetrievalExpectedRank> result = new ArrayList<>();
        for (String chunkId : item.getExpectedChunkIds()) {
            Integer rank = null;
            for (int index = 0; index < candidates.size(); index++) {
                if (chunkId.equals(candidates.get(index).getChunkId())) {
                    rank = index + 1;
                    break;
                }
            }
            result.add(new RetrievalExpectedRank("CHUNK", chunkId, rank));
        }
        return List.copyOf(result);
    }

    private Integer bestRank(
            List<RetrievalExpectedRank> claimRanks,
            List<RetrievalExpectedRank> chunkRanks
    ) {
        Integer best = null;
        List<RetrievalExpectedRank> combined = new ArrayList<>(
                claimRanks.size() + chunkRanks.size());
        combined.addAll(claimRanks);
        combined.addAll(chunkRanks);
        for (RetrievalExpectedRank expected : combined) {
            Integer rank = expected.getRank();
            if (rank != null && (best == null || rank < best)) {
                best = rank;
            }
        }
        return best;
    }
}
