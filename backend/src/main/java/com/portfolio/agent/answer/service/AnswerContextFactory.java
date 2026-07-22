package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.exception.InvalidAnswerContextException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public final class AnswerContextFactory {

    public ResolvedAnswerContext create(
            AnswerTurnSnapshot turnSnapshot,
            QuestionResolution resolution,
            AnswerRequest request
    ) {
        List<AnswerEvidence> evidence = resolution.getResolution() == AnswerResolution.ANSWERED
                ? resolution.getProject().getEvidence()
                : List.of();
        Set<String> allowedEvidenceIds = resolution.getProject().getEvidence().stream()
                .map(AnswerEvidence::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!allowedEvidenceIds.containsAll(request.getContext().getFocusEvidenceIds())) {
            throw new InvalidAnswerContextException();
        }
        return new ResolvedAnswerContext(
                turnSnapshot,
                resolution.getProject(),
                resolution.getQuestionPreset(),
                evidence
        );
    }
}
