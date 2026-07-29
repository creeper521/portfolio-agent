package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.ConversationDecision;

public interface ConversationDecisionPublisher {

    void publish(ConversationDecision decision);
}
