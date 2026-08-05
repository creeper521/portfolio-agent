package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.domain.StructuredSubjectResolution;

import java.util.List;
import java.util.Objects;

public final class StructuredSubjectResolver {

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
        return StructuredSubjectResolution.matched(matches.getFirst().getStableId());
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
}
