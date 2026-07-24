package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetrievalContextValidator {

    public RetrievalDecision validate(
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            Map<String, AnswerRetrievalChunk> chunks,
            List<RetrievalCandidate> candidates,
            RetrievalMode mode,
            RetrievalPolicy policy
    ) {
        if (candidates.isEmpty()) {
            return decision(RetrievalDecisionType.OUT_OF_SCOPE, mode, List.of(), List.of());
        }
        Map<String, AnswerClaimProjection> claimsById = new HashMap<>();
        for (AnswerClaimProjection claim : claims) {
            claimsById.put(claim.getId(), claim);
        }
        Set<String> approvedEvidenceIds = new HashSet<>();
        for (AnswerEvidence item : evidence) {
            if ("APPROVED".equals(item.getPublicStatus()) && !item.isRawContentPublic()) {
                approvedEvidenceIds.add(item.getId());
            }
        }

        List<RetrievalCandidate> limitedCandidates = candidates.stream()
                .limit(policy.getMaxChunks())
                .toList();
        if (isAmbiguous(limitedCandidates, chunks, policy)) {
            return decision(RetrievalDecisionType.AMBIGUOUS, mode, List.of(), List.of());
        }

        List<String> selectedChunkIds = new ArrayList<>();
        LinkedHashSet<String> selectedClaimIds = new LinkedHashSet<>();
        int contextCharacters = 0;
        for (RetrievalCandidate candidate : limitedCandidates) {
            AnswerRetrievalChunk chunk = chunks.get(candidate.getChunkId());
            if (chunk == null) {
                return decision(RetrievalDecisionType.INSUFFICIENT, mode, List.of(), List.of());
            }
            selectedChunkIds.add(chunk.getChunkId());
            contextCharacters += chunk.getTextLength();
            selectedClaimIds.addAll(chunk.getClaimIds());
        }
        if (contextCharacters > policy.getMaxContextCharacters()
                || selectedClaimIds.size() > policy.getMaxClaims()) {
            return decision(RetrievalDecisionType.INSUFFICIENT, mode, List.of(), List.of());
        }

        List<AnswerClaimProjection> selectedClaims = new ArrayList<>();
        boolean hasKeyClaim = false;
        for (String claimId : selectedClaimIds) {
            AnswerClaimProjection claim = claimsById.get(claimId);
            if (claim == null) {
                return decision(RetrievalDecisionType.INSUFFICIENT, mode, List.of(), List.of());
            }
            if (claim.getMateriality() == AnswerMateriality.KEY) {
                hasKeyClaim = true;
                if (claim.getDirectEvidenceIds().isEmpty()
                        || !approvedEvidenceIds.containsAll(claim.getDirectEvidenceIds())) {
                    return decision(
                            RetrievalDecisionType.INSUFFICIENT, mode, List.of(), List.of());
                }
            }
            selectedClaims.add(claim);
        }
        if (!hasKeyClaim) {
            return decision(RetrievalDecisionType.INSUFFICIENT, mode, List.of(), List.of());
        }
        if (hasLifecycleConflict(selectedClaims)) {
            return decision(RetrievalDecisionType.CONFLICTING, mode, List.of(), List.of());
        }
        return decision(
                RetrievalDecisionType.SUFFICIENT, mode,
                selectedChunkIds, List.copyOf(selectedClaimIds));
    }

    private boolean isAmbiguous(
            List<RetrievalCandidate> candidates,
            Map<String, AnswerRetrievalChunk> chunks,
            RetrievalPolicy policy
    ) {
        if (candidates.size() < 2 || candidates.get(0).getFusedScore() <= 0.0) {
            return false;
        }
        RetrievalCandidate first = candidates.get(0);
        RetrievalCandidate second = candidates.get(1);
        if (Integer.valueOf(1).equals(first.getKeywordRank())
                && Integer.valueOf(1).equals(first.getVectorRank())) {
            return false;
        }
        double relativeMargin = (first.getFusedScore() - second.getFusedScore())
                / first.getFusedScore();
        AnswerRetrievalChunk firstChunk = chunks.get(first.getChunkId());
        AnswerRetrievalChunk secondChunk = chunks.get(second.getChunkId());
        if (firstChunk == null || secondChunk == null) {
            return false;
        }
        return relativeMargin <= policy.getAmbiguityMargin()
                && java.util.Collections.disjoint(
                        firstChunk.getClaimIds(), secondChunk.getClaimIds());
    }

    private boolean hasLifecycleConflict(List<AnswerClaimProjection> claims) {
        Map<AnswerClaimCategory, Set<AnswerAchievementStatus>> statusByCategory = new HashMap<>();
        for (AnswerClaimProjection claim : claims) {
            statusByCategory.computeIfAbsent(
                    claim.getCategory(), ignored -> new HashSet<>())
                    .add(claim.getAchievementStatus());
        }
        for (Set<AnswerAchievementStatus> statuses : statusByCategory.values()) {
            boolean finalFact = statuses.contains(AnswerAchievementStatus.DELIVERED)
                    || statuses.contains(AnswerAchievementStatus.IMPLEMENTED_TESTED);
            boolean futureFact = statuses.contains(AnswerAchievementStatus.PLANNED)
                    || statuses.contains(AnswerAchievementStatus.UNKNOWN);
            if (finalFact && futureFact) {
                return true;
            }
        }
        return false;
    }

    private RetrievalDecision decision(
            RetrievalDecisionType type,
            RetrievalMode mode,
            List<String> chunkIds,
            List<String> claimIds
    ) {
        return new RetrievalDecision(type, mode, chunkIds, claimIds);
    }
}
