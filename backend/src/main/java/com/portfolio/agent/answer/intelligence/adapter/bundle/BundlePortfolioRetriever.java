package com.portfolio.agent.answer.intelligence.adapter.bundle;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class BundlePortfolioRetriever implements PortfolioRetriever {

    private static final PortfolioRetrievalSource SOURCE = new PortfolioRetrievalSource("BUNDLE");
    private static final String UNAVAILABLE_NOTICE = "BUNDLE_RETRIEVAL_UNAVAILABLE";

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final LocalRetrievalCoordinator retrievalCoordinator;
    private final RetrievalPolicy retrievalPolicy;

    public BundlePortfolioRetriever(
            PortfolioKnowledgeGateway knowledgeGateway,
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.retrievalCoordinator = Objects.requireNonNull(retrievalCoordinator, "retrievalCoordinator");
        this.retrievalPolicy = Objects.requireNonNull(retrievalPolicy, "retrievalPolicy");
    }

    @Override
    public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        if (content.getRetrievalCorpus().isEmpty()) {
            return new PortfolioRetrievalResult(
                    content.getContentVersion(), List.of(), List.of(), SOURCE, true, UNAVAILABLE_NOTICE);
        }
        AnswerRetrievalCorpus corpus = content.getRetrievalCorpus().orElseThrow();
        List<SubjectMaterial> materials = retrievePublishedSubjects(content, corpus, request);
        return new PortfolioRetrievalResult(
                content.getContentVersion(),
                materials.stream().map(SubjectMaterial::getSubject).toList(),
                materials.stream().flatMap(material -> material.getPassages().stream()).toList(),
                SOURCE,
                false,
                null);
    }

    private List<SubjectMaterial> retrievePublishedSubjects(
            RuntimeAnswerContent content,
            AnswerRetrievalCorpus corpus,
            PortfolioRetrievalRequest request) {
        List<SubjectMaterial> materials = new ArrayList<>();
        for (AnswerKnowledge knowledge : allKnowledge(content)) {
            SubjectMaterial material = retrieveSubject(knowledge, corpus, request);
            if (material != null) {
                materials.add(material);
            }
            if (materials.size() == request.getLimit()) {
                break;
            }
        }
        return List.copyOf(materials);
    }

    private List<AnswerKnowledge> allKnowledge(RuntimeAnswerContent content) {
        List<AnswerKnowledge> all = new ArrayList<>(content.getProjects());
        all.addAll(content.getCases());
        return List.copyOf(all);
    }

    private SubjectMaterial retrieveSubject(
            AnswerKnowledge knowledge,
            AnswerRetrievalCorpus corpus,
            PortfolioRetrievalRequest request) {
        if (request.isExactPortfolioLookup()
                && !request.getRequiredPortfolioIds().contains(knowledge.getStableId())) {
            return null;
        }
        String requestedCareerTrack = request.getConditions().getCareerTrack();
        if (requestedCareerTrack != null
                && !requestedCareerTrack.equals(knowledge.getCareerTrack())) {
            return null;
        }
        if (request.isExactPortfolioLookup()
                && !request.getConditions().getCapabilityCodes().isEmpty()
                && java.util.Collections.disjoint(
                        request.getConditions().getCapabilityCodes(),
                        knowledge.getCapabilityCodes())) {
            return null;
        }
        List<AnswerEvidence> approvedEvidence = knowledge.getEvidence().stream()
                .filter(this::isApprovedPublicEvidence)
                .toList();
        Set<String> approvedEvidenceIds = approvedEvidence.stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        List<AnswerClaimProjection> verifiedClaims = knowledge.getClaims().stream()
                .filter(claim -> claim.getVerificationStatus() == AnswerClaimVerificationStatus.VERIFIED)
                .filter(claim -> !claim.getDirectEvidenceIds().isEmpty())
                .filter(claim -> approvedEvidenceIds.containsAll(claim.getDirectEvidenceIds()))
                .toList();
        if (verifiedClaims.isEmpty()) {
            return null;
        }
        if (request.isExactPortfolioLookup()) {
            List<PortfolioRetrievedPassage> exactPassages = exactPassages(
                    knowledge, corpus, verifiedClaims);
            return exactPassages.isEmpty()
                    ? null
                    : new SubjectMaterial(toSubject(knowledge, request), exactPassages);
        }
        RetrievalDecision decision = retrievalCoordinator.retrieve(
                controlledRetrievalText(request),
                knowledge.getSlug(),
                knowledge.getSubjectType(),
                corpus,
                verifiedClaims,
                approvedEvidence,
                RetrievalMode.HYBRID_ENABLED,
                retrievalPolicy);
        if (decision.getType() != RetrievalDecisionType.SUFFICIENT) {
            return null;
        }
        List<PortfolioRetrievedPassage> passages = selectedPassages(
                knowledge, corpus, verifiedClaims, decision);
        if (passages.isEmpty()) {
            return null;
        }
        return new SubjectMaterial(toSubject(knowledge, request), passages);
    }

    private List<PortfolioRetrievedPassage> exactPassages(
            AnswerKnowledge knowledge,
            AnswerRetrievalCorpus corpus,
            List<AnswerClaimProjection> verifiedClaims) {
        Map<String, AnswerClaimProjection> claimsById = verifiedClaims.stream()
                .collect(Collectors.toMap(AnswerClaimProjection::getId, claim -> claim));
        List<PortfolioRetrievedPassage> passages = new ArrayList<>();
        corpus.getChunks().values().stream()
                .filter(chunk -> belongsToSubject(chunk, knowledge))
                .sorted(java.util.Comparator.comparing(AnswerRetrievalChunk::getChunkId))
                .forEach(chunk -> {
                    for (String claimId : chunk.getClaimIds()) {
                        AnswerClaimProjection claim = claimsById.get(claimId);
                        if (claim != null && chunk.getText() != null && !chunk.getText().isBlank()) {
                            passages.add(new PortfolioRetrievedPassage(
                                    chunk.getChunkId() + "#" + claimId,
                                    knowledge.getStableId(),
                                    claimId,
                                    chunk.getText(),
                                    claim.getDirectEvidenceIds().stream()
                                            .map(evidenceId -> evidenceReference(knowledge, evidenceId))
                                            .toList()));
                        }
                    }
                });
        return List.copyOf(passages);
    }

    private boolean belongsToSubject(
            AnswerRetrievalChunk chunk,
            AnswerKnowledge knowledge) {
        return knowledge.getSubjectType() == AnswerSubjectType.CASE
                ? chunk.getCaseSlugs().contains(knowledge.getSlug())
                : chunk.getProjectSlugs().contains(knowledge.getSlug());
    }

    private String controlledRetrievalText(PortfolioRetrievalRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add(request.getQuery());
        if (request.getConditions().getCareerTrack() != null) {
            parts.add(request.getConditions().getCareerTrack());
        }
        request.getConditions().getCapabilityCodes().stream()
                .sorted()
                .forEach(parts::add);
        return String.join(" ", parts);
    }

    private List<PortfolioRetrievedPassage> selectedPassages(
            AnswerKnowledge knowledge,
            AnswerRetrievalCorpus corpus,
            List<AnswerClaimProjection> verifiedClaims,
            RetrievalDecision decision) {
        Map<String, AnswerClaimProjection> claimsById = verifiedClaims.stream()
                .collect(Collectors.toMap(
                        AnswerClaimProjection::getId,
                        claim -> claim,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> selectedClaimIds = Set.copyOf(decision.getSelectedClaimIds());
        List<PortfolioRetrievedPassage> passages = new ArrayList<>();
        for (String chunkId : decision.getSelectedChunkIds()) {
            AnswerRetrievalChunk chunk = corpus.getChunks().get(chunkId);
            if (chunk == null || chunk.getText() == null || chunk.getText().isBlank()) {
                continue;
            }
            for (String claimId : chunk.getClaimIds()) {
                AnswerClaimProjection claim = claimsById.get(claimId);
                if (claim != null && selectedClaimIds.contains(claimId)) {
                    passages.add(new PortfolioRetrievedPassage(
                            chunkId + "#" + claimId,
                            knowledge.getStableId(),
                            claimId,
                            chunk.getText(),
                            claim.getDirectEvidenceIds().stream()
                                    .map(evidenceId -> evidenceReference(knowledge, evidenceId))
                                    .toList()));
                }
            }
        }
        return List.copyOf(passages);
    }

    private PortfolioRetrievedSubject toSubject(
            AnswerKnowledge knowledge,
            PortfolioRetrievalRequest request) {
        String routePrefix = knowledge.getSubjectType() == AnswerSubjectType.CASE
                ? "/cases/" : "/projects/";
        return new PortfolioRetrievedSubject(
                knowledge.getStableId(), knowledge.getSubjectType().name(), knowledge.getTitle(),
                knowledge.getSummary(), routePrefix + knowledge.getSlug(),
                knowledge.getCareerTrack(),
                knowledge.getCapabilityCodes(),
                targetFit(knowledge, request),
                1.0d,
                0.0d);
    }

    private PortfolioRetrievedEvidenceReference evidenceReference(
            AnswerKnowledge knowledge,
            String evidenceId) {
        AnswerEvidence evidence = knowledge.getEvidence().stream()
                .filter(candidate -> candidate.getId().equals(evidenceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "retrieved claim references missing evidence"));
        return new PortfolioRetrievedEvidenceReference(
                evidence.getId(), evidence.getTitle(), evidence.getPublicStatus());
    }

    private double targetFit(AnswerKnowledge knowledge, PortfolioRetrievalRequest request) {
        double careerFit = request.getConditions().getCareerTrack() == null
                || request.getConditions().getCareerTrack().equals(knowledge.getCareerTrack())
                ? 1.0d : 0.0d;
        Set<String> requestedCapabilities = request.getConditions().getCapabilityCodes();
        if (requestedCapabilities.isEmpty()) {
            return careerFit;
        }
        long matchedCapabilities = requestedCapabilities.stream()
                .filter(knowledge.getCapabilityCodes()::contains)
                .count();
        double capabilityFit = (double) matchedCapabilities / requestedCapabilities.size();
        return (careerFit + capabilityFit) / 2.0d;
    }

    private boolean isApprovedPublicEvidence(AnswerEvidence evidence) {
        return "APPROVED".equals(evidence.getPublicStatus()) && !evidence.isRawContentPublic();
    }

    private static final class SubjectMaterial {

        private final PortfolioRetrievedSubject subject;
        private final List<PortfolioRetrievedPassage> passages;

        private SubjectMaterial(PortfolioRetrievedSubject subject, List<PortfolioRetrievedPassage> passages) {
            this.subject = subject;
            this.passages = List.copyOf(passages);
        }

        private PortfolioRetrievedSubject getSubject() { return subject; }
        private List<PortfolioRetrievedPassage> getPassages() { return passages; }
    }
}
