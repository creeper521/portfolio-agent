package com.portfolio.agent.answer.application;

import com.portfolio.agent.answer.domain.model.AnswerResult;
import com.portfolio.agent.portfolio.application.exception.ProjectNotFoundException;
import com.portfolio.agent.portfolio.domain.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final PublicPortfolioRepository repository;
    private final AnswerEngine answerEngine;

    public AnswerService(PublicPortfolioRepository repository, AnswerEngine answerEngine) {
        this.repository = repository;
        this.answerEngine = answerEngine;
    }

    public AnswerResult answer(String projectSlug, String question) {
        boolean projectExists = repository.getSnapshot().getProjects().stream()
                .anyMatch(project -> project.getSlug().equals(projectSlug));
        if (!projectExists) {
            throw new ProjectNotFoundException(projectSlug);
        }
        return answerEngine.answer(projectSlug, question);
    }
}
