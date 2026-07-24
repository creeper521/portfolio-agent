package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import org.springframework.stereotype.Component;

@Component
public final class ConversationAnswerResponseMapper {

    public ConversationAnswerResponse toResponse(ConversationAnswerResult result) {
        return new ConversationAnswerResponse(result);
    }
}
