package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.exception.AnswerProjectNotFoundException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final AnswerEngine answerEngine;

    public AnswerService(
            PortfolioKnowledgeGateway knowledgeGateway,
            AnswerEngine answerEngine
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.answerEngine = answerEngine;
    }

    public AnswerResult answer(String projectSlug, String question) {
        AnswerKnowledge knowledge = knowledgeGateway.findBySlug(projectSlug)
                .orElseThrow(() -> new AnswerProjectNotFoundException(projectSlug));
        return answerEngine.answer(knowledge, question);
    }
}
