package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PortfolioGroundingAssembler {

    private static final Map<PortfolioKnowledgeFacet, Set<AnswerClaimCategory>>
            CATEGORIES_BY_FACET = categoryMap();

    private final int maxClaims;
    private final int maxEvidence;
    private final int maxCharacters;

    public PortfolioGroundingAssembler(
            int maxClaims,
            int maxEvidence,
            int maxCharacters
    ) {
        this.maxClaims = maxClaims;
        this.maxEvidence = maxEvidence;
        this.maxCharacters = maxCharacters;
    }

    public PortfolioGroundingContext assemble(
            RuntimeAnswerContent content,
            ConversationRoute route,
            String localQuestion
    ) {
        AnswerKnowledge knowledge = findSubject(content, route);
        if (knowledge == null) {
            return PortfolioGroundingContext.empty();
        }
        Set<AnswerClaimCategory> categories =
                CATEGORIES_BY_FACET.get(route.getFacet());
        List<AnswerClaimProjection> claims = knowledge.getClaims().stream()
                .filter(claim -> categories.contains(claim.getCategory()))
                .filter(this::isPubliclyGrounded)
                .limit(maxClaims)
                .toList();
        Set<String> evidenceIds = new LinkedHashSet<>();
        for (AnswerClaimProjection claim : claims) {
            evidenceIds.addAll(claim.getDirectEvidenceIds());
        }
        List<AnswerEvidence> evidence = knowledge.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> "APPROVED".equals(item.getPublicStatus()))
                .filter(item -> !item.isRawContentPublic())
                .limit(maxEvidence)
                .toList();
        Set<String> allowedEvidenceIds = evidence.stream()
                .map(AnswerEvidence::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<AnswerClaimProjection> supportedClaims = claims.stream()
                .filter(claim -> allowedEvidenceIds.containsAll(
                        claim.getDirectEvidenceIds()))
                .toList();
        List<AnswerRetrievalChunk> chunks = selectChunks(
                content, route, supportedClaims);
        return new PortfolioGroundingContext(
                new ConversationSubjectOption(
                        knowledge.getSubjectType(),
                        knowledge.getSlug(),
                        knowledge.getTitle(),
                        knowledge.getSummary()),
                supportedClaims,
                evidence,
                chunks);
    }

    public boolean canAnswer(
            RuntimeAnswerContent content,
            ConversationSuggestedQuestion question
    ) {
        ConversationRoute route = new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                com.portfolio.agent.answer.domain.ConversationAnswerScope.PORTFOLIO,
                1.0,
                question.getProjectSlug(),
                question.getCaseSlug(),
                question.getFacet() == null
                        ? PortfolioKnowledgeFacet.OVERVIEW : question.getFacet(),
                false);
        return !assemble(content, route, question.getText()).getClaims().isEmpty();
    }

    private List<AnswerRetrievalChunk> selectChunks(
            RuntimeAnswerContent content,
            ConversationRoute route,
            List<AnswerClaimProjection> claims
    ) {
        if (content.getRetrievalCorpus().isEmpty() || claims.isEmpty()) {
            return List.of();
        }
        Set<String> claimIds = claims.stream()
                .map(AnswerClaimProjection::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<AnswerRetrievalChunk> selected = new ArrayList<>();
        int characters = 0;
        for (AnswerRetrievalChunk chunk
                : content.getRetrievalCorpus().orElseThrow().getChunks().values()) {
            boolean subjectMatches = route.getProjectSlug() != null
                    ? chunk.getProjectSlugs().contains(route.getProjectSlug())
                    : chunk.getCaseSlugs().contains(route.getCaseSlug());
            boolean claimMatches = chunk.getClaimIds().stream().anyMatch(claimIds::contains);
            int chunkCharacters = chunk.getText() == null ? 0 : chunk.getText().length();
            if (subjectMatches && claimMatches
                    && characters + chunkCharacters <= maxCharacters) {
                selected.add(chunk);
                characters += chunkCharacters;
            }
        }
        return List.copyOf(selected);
    }

    private boolean isPubliclyGrounded(AnswerClaimProjection claim) {
        return claim.getVerificationStatus() == AnswerClaimVerificationStatus.VERIFIED
                && claim.getMateriality() == AnswerMateriality.KEY
                && !claim.getDirectEvidenceIds().isEmpty();
    }

    private AnswerKnowledge findSubject(
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        if (route.getProjectSlug() != null) {
            return content.getProjects().stream()
                    .filter(item -> route.getProjectSlug().equals(item.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        if (route.getCaseSlug() != null) {
            return content.getCases().stream()
                    .filter(item -> route.getCaseSlug().equals(item.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static Map<PortfolioKnowledgeFacet, Set<AnswerClaimCategory>> categoryMap() {
        EnumMap<PortfolioKnowledgeFacet, Set<AnswerClaimCategory>> map =
                new EnumMap<>(PortfolioKnowledgeFacet.class);
        map.put(PortfolioKnowledgeFacet.OVERVIEW,
                EnumSet.allOf(AnswerClaimCategory.class));
        map.put(PortfolioKnowledgeFacet.IMPLEMENTATION,
                Set.of(AnswerClaimCategory.IMPLEMENTATION,
                        AnswerClaimCategory.TECHNICAL_DECISION));
        map.put(PortfolioKnowledgeFacet.DECISION,
                Set.of(AnswerClaimCategory.TECHNICAL_DECISION));
        map.put(PortfolioKnowledgeFacet.CHALLENGE,
                Set.of(AnswerClaimCategory.BACKGROUND,
                        AnswerClaimCategory.LIMITATION));
        map.put(PortfolioKnowledgeFacet.INCIDENT,
                Set.of(AnswerClaimCategory.IMPLEMENTATION,
                        AnswerClaimCategory.VERIFICATION));
        map.put(PortfolioKnowledgeFacet.VERIFICATION,
                Set.of(AnswerClaimCategory.VERIFICATION,
                        AnswerClaimCategory.OUTCOME));
        map.put(PortfolioKnowledgeFacet.LIMITATION,
                Set.of(AnswerClaimCategory.LIMITATION));
        map.put(PortfolioKnowledgeFacet.LEARNING,
                Set.of(AnswerClaimCategory.OUTCOME,
                        AnswerClaimCategory.LIMITATION));
        return Map.copyOf(map);
    }
}
