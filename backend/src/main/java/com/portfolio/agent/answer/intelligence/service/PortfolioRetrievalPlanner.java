package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievalPlanner {

    public PortfolioRetrievalRequest planReference(
            PortfolioTurn turn,
            PortfolioReferenceResolution resolution,
            PortfolioTaskMode mode,
            PortfolioConditions conditions
    ) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(conditions, "conditions");
        PortfolioReferenceContext reference = Objects.requireNonNull(
                turn.getReferenceContext(), "referenceContext");
        return PortfolioRetrievalRequest.referenceScope(
                turn.getQuestion(),
                mode,
                conditions,
                resolution.getSubjectIds(),
                resolution.getClaimIds(),
                preferredCategories(reference.getSelectedSectionType()));
    }

    private List<AnswerClaimCategory> preferredCategories(AnswerSectionType sectionType) {
        if (sectionType == null) {
            return List.of();
        }
        return switch (sectionType) {
            case BACKGROUND -> List.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> List.of(AnswerClaimCategory.RESPONSIBILITY);
            case SOLUTION -> List.of(
                    AnswerClaimCategory.TECHNICAL_DECISION,
                    AnswerClaimCategory.IMPLEMENTATION);
            case VERIFICATION -> List.of(AnswerClaimCategory.VERIFICATION);
            case STATUS -> List.of(
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.LIMITATION);
            case BOUNDARY, REJECTED -> List.of();
        };
    }
}
