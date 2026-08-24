package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerEvidence;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKeywordIndex;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalChunk;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalCorpus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerSubjectType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 从随包公开内容直接生成最终候选模型，不经过旧 Answer 检索协议。 */
public final class BundlePortfolioRetrieverAdapter implements PortfolioRetrieverPort {

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final LocalEmbeddingPort embeddingPort;
    private final boolean hybridEnabled;

    public BundlePortfolioRetrieverAdapter(
            PortfolioKnowledgeGateway knowledgeGateway,
            LocalEmbeddingPort embeddingPort,
            boolean hybridEnabled) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.hybridEnabled = hybridEnabled;
    }

    @Override
    public RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deadline, "deadline");
        if (deadline.isExpired()) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
        }
        try {
            RuntimeAnswerContent content = knowledgeGateway.getContent();
            if (!invocation.getContentReleaseId().equals(content.getContentVersion())) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.INTEGRITY_FAILURE);
            }
            if (content.getRetrievalCorpus().isEmpty()) {
                return RetrievalAttemptResult.success(new PortfolioCandidateSet(
                        content.getContentVersion(), invocation.getSubjectScope(), List.of()));
            }
            PortfolioCandidateSet candidateSet = candidateSet(
                    invocation, request.getStrategy(), content,
                    content.getRetrievalCorpus().orElseThrow(), deadline);
            return deadline.isExpired()
                    ? RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED)
                    : RetrievalAttemptResult.success(candidateSet);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.INTEGRITY_FAILURE);
        }
    }

    private PortfolioCandidateSet candidateSet(
            PortfolioEvidenceInvocation invocation,
            SearchStrategy strategy,
            RuntimeAnswerContent content,
            AnswerRetrievalCorpus corpus,
            TurnDeadline deadline) {
        List<AnswerKnowledge> knowledge = new ArrayList<>(content.getProjects());
        knowledge.addAll(content.getCases());
        List<AnswerClaimCategory> categories = categories(invocation);
        List<String> rankedChunkIds = rankedChunkIds(
                invocation, strategy, corpus, knowledge, categories);
        Map<String, Integer> chunkRanks = new LinkedHashMap<>();
        for (int index = 0; index < rankedChunkIds.size(); index++) {
            chunkRanks.put(rankedChunkIds.get(index), index);
        }
        knowledge.sort(java.util.Comparator
                .comparingInt((AnswerKnowledge subject) -> subjectRank(subject, corpus, chunkRanks))
                .thenComparing(AnswerKnowledge::getStableId));
        List<CandidateSubject> subjects = new ArrayList<>();
        for (AnswerKnowledge subject : knowledge) {
            if (deadline.isExpired()) {
                break;
            }
            if (!authorized(invocation.getSubjectScope(), subject)) {
                continue;
            }
            List<ClaimEvidenceCandidate> candidates = candidates(
                    content.getContentVersion(), subject, corpus, categories, chunkRanks,
                    invocation.getMaximumEvidenceUnitsPerSubject());
            if (!candidates.isEmpty()) {
                subjects.add(new CandidateSubject(
                        subject.getStableId(), route(subject), subject.getTitle(),
                        content.getContentVersion(), subject.getCareerTrack(),
                        subject.getCapabilityCodes(), candidates));
            }
        }
        return new PortfolioCandidateSet(
                content.getContentVersion(), invocation.getSubjectScope(), subjects);
    }

    private List<ClaimEvidenceCandidate> candidates(
            String contentVersion,
            AnswerKnowledge subject,
            AnswerRetrievalCorpus corpus,
            List<AnswerClaimCategory> categories,
            Map<String, Integer> chunkRanks,
            int maximumEvidenceUnits) {
        Map<String, AnswerEvidence> approvedEvidence = subject.getEvidence().stream()
                .filter(this::isApprovedPublicEvidence)
                .collect(Collectors.toMap(
                        AnswerEvidence::getId,
                        evidence -> evidence,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> retrievableClaimIds = corpus.getChunks().values().stream()
                .filter(chunk -> belongsToSubject(chunk, subject))
                .filter(chunk -> chunkRanks.containsKey(chunk.getChunkId()))
                .filter(chunk -> chunk.getText() != null && !chunk.getText().isBlank())
                .sorted(java.util.Comparator.comparingInt(
                        chunk -> chunkRanks.get(chunk.getChunkId())))
                .flatMap(chunk -> chunk.getClaimIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ClaimEvidenceCandidate> candidates = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        subject.getClaims().stream()
                .filter(claim -> claim.getVerificationStatus()
                        == AnswerClaimVerificationStatus.VERIFIED)
                .filter(claim -> categories.isEmpty() || categories.contains(claim.getCategory()))
                .filter(claim -> retrievableClaimIds.contains(claim.getId()))
                .takeWhile(claim -> candidates.size() < maximumEvidenceUnits)
                .forEach(claim -> claim.getDirectEvidenceIds().stream()
                        .takeWhile(evidenceId -> candidates.size() < maximumEvidenceUnits)
                        .forEach(evidenceId -> {
                    AnswerEvidence evidence = approvedEvidence.get(evidenceId);
                    String identity = claim.getId() + "\u0000" + evidenceId;
                    if (evidence == null || !identities.add(identity)) {
                        return;
                    }
                    candidates.add(new ClaimEvidenceCandidate(
                            subject.getStableId(), claim,
                            descriptor(contentVersion, route(subject), evidence),
                            claim.getCategory().name()));
                }));
        return List.copyOf(candidates);
    }

    private List<String> rankedChunkIds(
            PortfolioEvidenceInvocation invocation,
            SearchStrategy strategy,
            AnswerRetrievalCorpus corpus,
            List<AnswerKnowledge> knowledge,
            List<AnswerClaimCategory> categories) {
        Set<String> eligibleClaims = knowledge.stream()
                .filter(subject -> authorized(invocation.getSubjectScope(), subject))
                .flatMap(subject -> subject.getClaims().stream())
                .filter(claim -> claim.getVerificationStatus()
                        == AnswerClaimVerificationStatus.VERIFIED)
                .filter(claim -> categories.isEmpty() || categories.contains(claim.getCategory()))
                .map(claim -> claim.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> eligibleChunks = corpus.getChunks().values().stream()
                .filter(chunk -> chunk.getText() != null && !chunk.getText().isBlank())
                .filter(chunk -> chunk.getClaimIds().stream().anyMatch(eligibleClaims::contains))
                .filter(chunk -> knowledge.stream()
                        .filter(subject -> authorized(invocation.getSubjectScope(), subject))
                        .anyMatch(subject -> belongsToSubject(chunk, subject)))
                .map(AnswerRetrievalChunk::getChunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (strategy == SearchStrategy.EXACT) {
            return eligibleChunks.stream().sorted().toList();
        }
        List<String> terms = queryTerms(invocation);
        List<String> keyword = keywordRanking(corpus, eligibleChunks, terms);
        if (strategy == SearchStrategy.KEYWORD || !hybridEnabled) {
            return appendUnranked(keyword, eligibleChunks);
        }
        List<String> vector = vectorRanking(corpus, eligibleChunks, String.join(" ", terms));
        if (vector.isEmpty()) {
            return appendUnranked(keyword, eligibleChunks);
        }
        return appendUnranked(fuse(keyword, vector), eligibleChunks);
    }

    private List<String> keywordRanking(
            AnswerRetrievalCorpus corpus,
            Set<String> eligibleChunks,
            List<String> terms) {
        List<ChunkScore> scores = new ArrayList<>();
        AnswerKeywordIndex index = corpus.getKeywordIndex();
        for (AnswerKeywordIndex.DocumentEntry document : index.getDocuments()) {
            if (!eligibleChunks.contains(document.getChunkId())) {
                continue;
            }
            double score = 0.0d;
            for (String term : new LinkedHashSet<>(terms)) {
                int frequency = document.getTermFrequencies().getOrDefault(term, 0);
                int documentFrequency = index.getDocumentFrequencies().getOrDefault(term, 0);
                if (frequency == 0 || documentFrequency == 0) {
                    continue;
                }
                double inverseDocumentFrequency = Math.log(
                        1.0d + (index.getDocumentCount() - documentFrequency + 0.5d)
                                / (documentFrequency + 0.5d));
                double lengthRatio = index.getAverageDocumentLength() == 0.0d
                        ? 0.0d
                        : document.getDocumentLength() / index.getAverageDocumentLength();
                double denominator = frequency + 1.2d * (1.0d - 0.75d + 0.75d * lengthRatio);
                score += inverseDocumentFrequency * frequency * 2.2d / denominator;
            }
            if (score > 0.0d) {
                scores.add(new ChunkScore(document.getChunkId(), score));
            }
        }
        return sorted(scores);
    }

    private List<String> vectorRanking(
            AnswerRetrievalCorpus corpus,
            Set<String> eligibleChunks,
            String controlledQuery) {
        try {
            EmbeddingVector query = embeddingPort.embedQuery(controlledQuery);
            float[] queryValues = query.copyValues();
            List<ChunkScore> scores = new ArrayList<>();
            for (Map.Entry<String, float[]> entry : corpus.copyVectors().entrySet()) {
                if (!eligibleChunks.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue().length != queryValues.length) {
                    return List.of();
                }
                double score = 0.0d;
                for (int index = 0; index < queryValues.length; index++) {
                    score += queryValues[index] * entry.getValue()[index];
                }
                scores.add(new ChunkScore(entry.getKey(), score));
            }
            return sorted(scores);
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private List<String> fuse(List<String> keyword, List<String> vector) {
        Map<String, Double> scores = new LinkedHashMap<>();
        addRanks(scores, keyword);
        addRanks(scores, vector);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addRanks(Map<String, Double> scores, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            scores.merge(values.get(index), 1.0d / (61.0d + index), Double::sum);
        }
    }

    private List<String> appendUnranked(List<String> ranked, Set<String> eligible) {
        LinkedHashSet<String> all = new LinkedHashSet<>(ranked);
        eligible.stream().sorted().forEach(all::add);
        return List.copyOf(all);
    }

    private List<String> sorted(List<ChunkScore> scores) {
        return scores.stream()
                .sorted(java.util.Comparator.comparingDouble(ChunkScore::score).reversed()
                        .thenComparing(ChunkScore::chunkId))
                .map(ChunkScore::chunkId)
                .toList();
    }

    private int subjectRank(
            AnswerKnowledge subject,
            AnswerRetrievalCorpus corpus,
            Map<String, Integer> chunkRanks) {
        return corpus.getChunks().values().stream()
                .filter(chunk -> belongsToSubject(chunk, subject))
                .map(chunk -> chunkRanks.get(chunk.getChunkId()))
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    private List<String> queryTerms(PortfolioEvidenceInvocation invocation) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        invocation.getFacets().forEach(facet -> terms.addAll(switch (facet) {
            case BACKGROUND -> List.of("背景", "项目");
            case RESPONSIBILITY -> List.of("职责", "负责");
            case IMPLEMENTATION -> List.of("实现", "技术");
            case TECHNICAL_DECISION -> List.of("决策", "架构");
            case VERIFICATION -> List.of("验证", "测试");
            case OUTCOME -> List.of("结果", "完成");
            case LIMITATION -> List.of("限制", "边界");
            case RECOMMENDATION -> List.of("项目", "技术", "实现", "验证", "结果");
        }));
        invocation.getDimensions().stream()
                .map(String::toLowerCase)
                .forEach(terms::add);
        invocation.getRecommendationConstraints().stream()
                .map(String::toLowerCase)
                .forEach(terms::add);
        return List.copyOf(terms);
    }

    private record ChunkScore(String chunkId, double score) {
    }

    private PublicEvidenceDescriptor descriptor(
            String contentVersion, String subjectRoute, AnswerEvidence evidence) {
        return new PublicEvidenceDescriptor(
                evidence.getId(), evidence.getCode(), evidence.getTitle(), contentVersion,
                evidence.getPublicStatus(),
                PublicEvidenceDescriptor.SourceType.valueOf(evidence.getType()),
                subjectRoute, "/evidence?evidence=" + evidence.getId(),
                LocalDate.of(9999, 12, 31));
    }

    private boolean authorized(AuthorizedSubjectScope scope, AnswerKnowledge subject) {
        if (scope.getMode() == AuthorizedSubjectScope.Mode.ALL_PUBLISHED) {
            return true;
        }
        return scope.getSubjects().stream().anyMatch(reference ->
                reference.getReference().equals(subject.getStableId()));
    }

    private boolean belongsToSubject(AnswerRetrievalChunk chunk, AnswerKnowledge subject) {
        return subject.getSubjectType() == AnswerSubjectType.CASE
                ? chunk.getCaseSlugs().contains(subject.getSlug())
                : chunk.getProjectSlugs().contains(subject.getSlug());
    }

    private String route(AnswerKnowledge subject) {
        return (subject.getSubjectType() == AnswerSubjectType.CASE
                ? "/cases/" : "/projects/") + subject.getSlug();
    }

    private boolean isApprovedPublicEvidence(AnswerEvidence evidence) {
        return "APPROVED".equals(evidence.getPublicStatus()) && !evidence.isRawContentPublic();
    }

    private List<AnswerClaimCategory> categories(PortfolioEvidenceInvocation invocation) {
        LinkedHashSet<AnswerClaimCategory> categories = new LinkedHashSet<>();
        invocation.getFacets().forEach(facet -> categories.addAll(switch (facet) {
            case BACKGROUND -> List.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> List.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION -> List.of(AnswerClaimCategory.IMPLEMENTATION);
            case TECHNICAL_DECISION -> List.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case VERIFICATION -> List.of(AnswerClaimCategory.VERIFICATION);
            case OUTCOME -> List.of(AnswerClaimCategory.OUTCOME);
            case LIMITATION -> List.of(AnswerClaimCategory.LIMITATION);
            case RECOMMENDATION -> List.of(
                    AnswerClaimCategory.BACKGROUND,
                    AnswerClaimCategory.IMPLEMENTATION,
                    AnswerClaimCategory.VERIFICATION,
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.TECHNICAL_DECISION);
        }));
        invocation.getDimensions().forEach(dimension -> categories.add(switch (dimension) {
            case "ARCHITECTURE" -> AnswerClaimCategory.TECHNICAL_DECISION;
            case "IMPLEMENTATION" -> AnswerClaimCategory.IMPLEMENTATION;
            case "OUTCOME" -> AnswerClaimCategory.OUTCOME;
            case "RISKS" -> AnswerClaimCategory.LIMITATION;
            case "VERIFICATION" -> AnswerClaimCategory.VERIFICATION;
            default -> throw new IllegalArgumentException(
                    "unsupported portfolio comparison dimension");
        }));
        return List.copyOf(categories);
    }
}
