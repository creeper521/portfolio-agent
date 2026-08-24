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
        int maximumSections = maximumSections(result);
        for (com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit
                : result.getUnits()) {
            if (sections.size() >= maximumSections) break;
            String content = content(result, unit);
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

    private int maximumSections(PortfolioSemanticResult result) {
        if (!(result instanceof PortfolioSemanticResult.Fact fact)) {
            return policy.getMaximumSections();
        }
        int depthMaximum = switch (fact.getDepth()) {
            case CONCISE -> 2;
            case STANDARD, DETAILED -> 8;
        };
        return Math.min(policy.getMaximumSections(), depthMaximum);
    }

    private String content(
            PortfolioSemanticResult result,
            com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit) {
        String statement = unit.getClaim().getStatement();
        if (!(result instanceof PortfolioSemanticResult.Fact fact)
                || fact.getDepth() != com.portfolio.agent.turn.planning.UserGoalProposal.Depth.DETAILED
                || unit.getClaim().getDetail() == null
                || unit.getClaim().getDetail().isBlank()
                || unit.getClaim().getDetail().equals(statement)) {
            return statement;
        }
        return statement + "\n" + unit.getClaim().getDetail();
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
