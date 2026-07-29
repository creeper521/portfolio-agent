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

    private static final int MAX_TITLE_CODE_POINTS = 120;
    private static final int MAX_BLOCKS = 8;
    private static final int MAX_BLOCK_CODE_POINTS = 2_000;
    private static final int MAX_TOTAL_CODE_POINTS = 8_000;

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
        if (codePoints(draft.getTitle()) > MAX_TITLE_CODE_POINTS) {
            return ConversationDraftValidationResult.invalid("TITLE_TOO_LONG");
        }
        if (draft.getBlocks().size() > MAX_BLOCKS) {
            return ConversationDraftValidationResult.invalid("TOO_MANY_BLOCKS");
        }
        int totalCodePoints = 0;
        for (ConversationAnswerBlock block : draft.getBlocks()) {
            if (block.getContent().isBlank()) {
                return ConversationDraftValidationResult.invalid("EMPTY_BLOCK_CONTENT");
            }
            int blockCodePoints = codePoints(block.getContent());
            if (blockCodePoints > MAX_BLOCK_CODE_POINTS) {
                return ConversationDraftValidationResult.invalid("BLOCK_TOO_LONG");
            }
            totalCodePoints += blockCodePoints;
            if (totalCodePoints > MAX_TOTAL_CODE_POINTS) {
                return ConversationDraftValidationResult.invalid("ANSWER_TOO_LONG");
            }
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

    private int codePoints(String value) {
        return value.codePointCount(0, value.length());
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
