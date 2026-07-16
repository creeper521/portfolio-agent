package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.AnswerKnowledge;

import java.util.Optional;

public interface PortfolioKnowledgeGateway {

    Optional<AnswerKnowledge> findBySlug(String projectSlug);
}
