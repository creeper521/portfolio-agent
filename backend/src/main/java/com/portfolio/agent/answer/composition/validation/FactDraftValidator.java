package com.portfolio.agent.answer.composition.validation;

import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import java.util.List;

public final class FactDraftValidator {
    private final StatementGroundingValidator groundingValidator = new StatementGroundingValidator();

    public void validate(FactExpressionDraft draft, FactAnswerMaterial material,
            ExpressionAliasRegistry aliases, PortfolioCompositionContext context) {
        validateSummary(draft, material.getFocusMode());
        List<String> expected = material.getSections().stream()
                .map(section -> section.getSectionType().name()).toList();
        List<String> actual = draft.getSections().stream()
                .map(section -> section.getSectionType().name()).toList();
        if (!actual.equals(expected)) {
            throw new GroundingValidationException(GroundingValidationException.Code.TASK_SCOPE);
        }
        validateSectionScopes(draft, aliases);
        groundingValidator.validate(draft, aliases);
    }

    private static void validateSummary(FactExpressionDraft draft, FocusMode focusMode) {
        if (focusMode == FocusMode.FOCUSED && draft.getSummary() != null) {
            throw new GroundingValidationException(GroundingValidationException.Code.TASK_SCOPE);
        }
        if (focusMode == FocusMode.OVERVIEW && draft.getSummary() == null) {
            throw new GroundingValidationException(GroundingValidationException.Code.TASK_SCOPE);
        }
    }

    private static void validateSectionScopes(FactExpressionDraft draft,
            ExpressionAliasRegistry aliases) {
        draft.getSections().forEach(section -> section.getSentences().forEach(sentence ->
                sentence.getSupports().forEach(alias -> {
                    com.portfolio.agent.answer.composition.domain.ExpressionStatement entry =
                            aliases.expressionStatement(alias);
                    if (entry == null || entry.getAllowedSection() != section.getSectionType()) {
                        throw new GroundingValidationException(
                                GroundingValidationException.Code.TASK_SCOPE);
                    }
                })));
    }
}
