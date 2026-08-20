package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.Optional;

public interface PortfolioKnowledgeGateway {

    RuntimeAnswerContent getContent();

    default Optional<AnswerKnowledge> findBySlug(String projectSlug) {
        return getContent().getProjects().stream()
                .filter(project -> project.getSlug().equals(projectSlug))
                .findFirst();
    }
}
