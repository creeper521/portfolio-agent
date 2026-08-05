package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolution;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolutionType;

import java.util.List;
import java.util.Objects;

public final class StructuredSubjectTaskResolver {

    public StructuredSubjectResolution resolve(
            PortfolioTurn turn,
            RuntimeAnswerContent content
    ) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(content, "content");
        if (turn.getProjectSlug() == null && turn.getCaseSlug() == null) {
            return StructuredSubjectResolution.none();
        }
        List<AnswerKnowledge> matches = turn.getProjectSlug() != null
                ? matchingProjects(turn.getProjectSlug(), content)
                : matchingCases(turn.getCaseSlug(), content);
        if (matches.size() != 1) {
            return StructuredSubjectResolution.invalid();
        }
        return StructuredSubjectResolution.matched(
                factLookup(turn, matches.getFirst()));
    }

    private List<AnswerKnowledge> matchingProjects(
            String slug,
            RuntimeAnswerContent content
    ) {
        return content.getProjects().stream()
                .filter(subject -> slug.equals(subject.getSlug()))
                .toList();
    }

    private List<AnswerKnowledge> matchingCases(
            String slug,
            RuntimeAnswerContent content
    ) {
        return content.getCases().stream()
                .filter(subject -> slug.equals(subject.getSlug()))
                .toList();
    }

    private PortfolioTask factLookup(
            PortfolioTurn turn,
            AnswerKnowledge subject
    ) {
        return new PortfolioTask(
                turn.getTurnId(),
                turn.getQuestion(),
                PortfolioTaskMode.FACT_LOOKUP,
                1.0d,
                PortfolioConditions.empty(),
                turn.getRecommendationContext(),
                null,
                subject.getStableId());
    }
}
