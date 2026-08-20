package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PortfolioPresentationComposer {
    private final PresentationPolicy policy;
    public PortfolioPresentationComposer(PresentationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public PortfolioPresentation compose(PortfolioSemanticResult result) {
        List<PortfolioPresentation.Section> sections = new ArrayList<>();
        int characters = 0;
        for (com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit
                : result.getUnits()) {
            if (sections.size() >= policy.getMaximumSections()) break;
            String content = unit.getClaim().getStatement();
            int next = characters + content.length();
            if (next > policy.getMaximumCharacters()) break;
            sections.add(new PortfolioPresentation.Section(
                    section(unit.getClaim().getCategory()), label(unit.getClaim().getCategory()),
                    content, List.of(source(unit.getSourceReference()))));
            characters = next;
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("supported semantic result exceeds presentation bounds");
        }
        return new PortfolioPresentation("回答", sections);
    }

    private AnswerSectionType section(AnswerClaimCategory category) {
        return switch (category) {
            case BACKGROUND -> AnswerSectionType.BACKGROUND;
            case RESPONSIBILITY -> AnswerSectionType.RESPONSIBILITY;
            case VERIFICATION -> AnswerSectionType.VERIFICATION;
            case OUTCOME -> AnswerSectionType.STATUS;
            case LIMITATION, LEARNING, REFLECTION -> AnswerSectionType.BOUNDARY;
            default -> AnswerSectionType.SOLUTION;
        };
    }
    private String label(AnswerClaimCategory category) {
        return switch (section(category)) {
            case BACKGROUND -> "背景";
            case RESPONSIBILITY -> "职责";
            case VERIFICATION -> "验证";
            case STATUS -> "状态";
            case BOUNDARY -> "边界";
            default -> "方案";
        };
    }
    private PublicSourceReferenceValue source(
            com.portfolio.agent.turn.execution.PublicSourceReferenceValue value) {
        return new PublicSourceReferenceValue(
                value.getReferenceKey(), value.getLabel(), value.getPublishedVersion(),
                value.getSourceType(), value.getSubjectRoute(), value.getEvidenceRoute());
    }
}
