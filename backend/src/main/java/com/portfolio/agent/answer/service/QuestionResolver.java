package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.engine.QuestionNormalizer;
import com.portfolio.agent.answer.exception.AnswerCaseNotFoundException;
import com.portfolio.agent.answer.exception.AnswerProjectNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class QuestionResolver {

    private final QuestionNormalizer normalizer;

    public QuestionResolver(QuestionNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public QuestionResolution resolve(RuntimeAnswerContent content, AnswerRequest request) {
        AnswerKnowledge project = resolveSubject(content, request);

        if (requestsPrivateMaterial(request.getQuestion())) {
            return new QuestionResolution(AnswerResolution.REJECTED, project, null);
        }

        AnswerQuestion preset = findPreset(project, request);
        if (preset == null) {
            return new QuestionResolution(AnswerResolution.BOUNDARY, project, null);
        }
        return new QuestionResolution(AnswerResolution.ANSWERED, project, preset);
    }

    private AnswerKnowledge resolveSubject(
            RuntimeAnswerContent content,
            AnswerRequest request
    ) {
        if (request.getContext().getCaseSlug() != null) {
            return content.getCases().stream()
                    .filter(candidate -> candidate.getSlug()
                            .equals(request.getContext().getCaseSlug()))
                    .findFirst()
                    .orElseThrow(() -> new AnswerCaseNotFoundException(
                            request.getContext().getCaseSlug()));
        }
        return content.getProjects().stream()
                .filter(candidate -> candidate.getSlug()
                        .equals(request.getContext().getProjectSlug()))
                .findFirst()
                .orElseThrow(() -> new AnswerProjectNotFoundException(
                        request.getContext().getProjectSlug()));
    }

    private AnswerQuestion findPreset(AnswerKnowledge project, AnswerRequest request) {
        if (request.getQuestionPresetId() != null && !request.getQuestionPresetId().isBlank()) {
            return project.getQuestions().stream()
                    .filter(candidate -> candidate.getId().equals(request.getQuestionPresetId()))
                    .findFirst()
                    .orElse(null);
        }
        String normalized = normalizer.normalize(request.getQuestion());
        return project.getQuestions().stream()
                .filter(candidate -> matches(candidate, normalized))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(AnswerQuestion candidate, String normalized) {
        if (normalized.isBlank()) {
            return false;
        }
        if (normalizer.normalize(candidate.getCanonicalQuestion()).equals(normalized)) {
            return true;
        }
        return candidate.getAliases().stream()
                .map(normalizer::normalize)
                .anyMatch(normalized::equals);
    }

    private boolean requestsPrivateMaterial(String question) {
        if (question == null) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        boolean privateTarget = normalized.contains("内部") || normalized.contains("私有")
                || normalized.contains("private");
        boolean credential = normalized.contains("密码") || normalized.contains("token")
                || normalized.contains("密钥") || normalized.contains("credential");
        return privateTarget && credential;
    }
}
