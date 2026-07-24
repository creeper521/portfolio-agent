package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.ConversationMessage;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface ConversationSummaryPort {

    Optional<String> summarize(List<ConversationMessage> messages);
}
