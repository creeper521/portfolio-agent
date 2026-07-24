package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/answers")
public final class ConversationAnswerController {

    private final ConversationalAgentRuntime runtime;
    private final ConversationAnswerResponseMapper responseMapper;

    public ConversationAnswerController(
            ConversationalAgentRuntime runtime,
            ConversationAnswerResponseMapper responseMapper
    ) {
        this.runtime = runtime;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public ConversationAnswerResponse answer(
            @Valid @RequestBody ConversationAnswerRequest request
    ) {
        ConversationAnswerResult result = runtime.answer(request);
        return responseMapper.toResponse(result);
    }
}
