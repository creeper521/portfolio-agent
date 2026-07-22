package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.AnswerDecision;

public interface AnswerDecisionPublisher {

    void publish(AnswerDecision decision);
}
