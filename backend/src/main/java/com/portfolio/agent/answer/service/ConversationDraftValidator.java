package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.GroundingReview;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConversationDraftValidator {

    private final ConversationalModelPort modelPort;

    public ConversationDraftValidator(ConversationalModelPort modelPort) {
        this.modelPort = modelPort;
    }

    public ConversationDraftValidationResult validate(
            ConversationDraft draft,
            ConversationAnswerScope scope,
            PortfolioGroundingContext grounding
    ) {
        if (draft == null || draft.getTitle() == null
                || draft.getTitle().isBlank()
                || draft.getResolution() == null
                || draft.getBlocks().isEmpty()) {
            return ConversationDraftValidationResult.invalid("INVALID_DRAFT_SHAPE");
        }
        Set<String> allowedClaimIds = grounding.getClaims().stream()
                .map(AnswerClaimProjection::getId)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> allowedEvidenceIds = grounding.getEvidence().stream()
                .map(com.portfolio.agent.answer.domain.AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, Set<String>> directEvidenceByClaim = grounding.getClaims().stream()
                .collect(Collectors.toUnmodifiableMap(
                        AnswerClaimProjection::getId,
                        claim -> Set.copyOf(claim.getDirectEvidenceIds())));
        for (ConversationAnswerBlock block : draft.getBlocks()) {
            String failure = validateBlock(
                    block,
                    allowedClaimIds,
                    allowedEvidenceIds,
                    directEvidenceByClaim);
            if (failure != null) {
                return ConversationDraftValidationResult.invalid(failure);
            }
        }
        if (!scopeMatches(scope, draft.getBlocks())) {
            return ConversationDraftValidationResult.invalid("ANSWER_SCOPE_MISMATCH");
        }
        ConversationModelResult<GroundingReview> review =
                modelPort.review(draft.getBlocks(), grounding);
        if (review == null || !review.isSuccessful()) {
            return ConversationDraftValidationResult.invalid("SEMANTIC_REVIEW_FAILED");
        }
        if (!review.getValue().getUnsupportedBlockIndexes().isEmpty()) {
            return ConversationDraftValidationResult.invalid("UNSUPPORTED_BLOCK");
        }
        return ConversationDraftValidationResult.valid(draft, draft.getBlocks());
    }

    private String validateBlock(
            ConversationAnswerBlock block,
            Set<String> allowedClaimIds,
            Set<String> allowedEvidenceIds,
            Map<String, Set<String>> directEvidenceByClaim
    ) {
        if (block.getSourceScope() == ConversationSourceScope.GENERAL
                && (!block.getClaimIds().isEmpty()
                || !block.getEvidenceIds().isEmpty())) {
            return "UNEXPECTED_GENERAL_REFERENCES";
        }
        if (block.getSourceScope() == ConversationSourceScope.PORTFOLIO
                && (block.getClaimIds().isEmpty()
                || block.getEvidenceIds().isEmpty())) {
            return "MISSING_PORTFOLIO_REFERENCES";
        }
        if (!allowedClaimIds.containsAll(block.getClaimIds())
                || !allowedEvidenceIds.containsAll(block.getEvidenceIds())) {
            return "UNKNOWN_REFERENCE";
        }
        if (block.getSourceScope() == ConversationSourceScope.PORTFOLIO) {
            Set<String> direct = block.getClaimIds().stream()
                    .flatMap(id -> directEvidenceByClaim
                            .getOrDefault(id, Set.of()).stream())
                    .collect(Collectors.toUnmodifiableSet());
            if (!direct.containsAll(block.getEvidenceIds())) {
                return "NON_DIRECT_EVIDENCE";
            }
        }
        return null;
    }

    private boolean scopeMatches(
            ConversationAnswerScope scope,
            List<ConversationAnswerBlock> blocks
    ) {
        boolean hasGeneral = blocks.stream()
                .anyMatch(block -> block.getSourceScope()
                        == ConversationSourceScope.GENERAL);
        boolean hasPortfolio = blocks.stream()
                .anyMatch(block -> block.getSourceScope()
                        == ConversationSourceScope.PORTFOLIO);
        return switch (scope) {
            case CONVERSATION, GENERAL -> hasGeneral && !hasPortfolio;
            case PORTFOLIO -> hasPortfolio && !hasGeneral;
            case HYBRID -> hasGeneral && hasPortfolio;
        };
    }
}
