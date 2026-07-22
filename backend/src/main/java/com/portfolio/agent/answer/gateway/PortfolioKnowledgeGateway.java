package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.AnswerKnowledge;

import java.util.Optional;

public interface PortfolioKnowledgeGateway {

    RuntimeAnswerContent getContent();

    default Optional<AnswerKnowledge> findBySlug(String projectSlug) {
        return getContent().getProjects().stream()
                .filter(project -> project.getSlug().equals(projectSlug))
                .findFirst();
    }
}
