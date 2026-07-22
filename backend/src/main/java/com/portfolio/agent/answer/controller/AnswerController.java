package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.mapper.AnswerResponseMapper;
import com.portfolio.agent.answer.service.PortfolioAgentRuntime;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final PortfolioAgentRuntime runtime;
    private final AnswerResponseMapper responseMapper;

    public AnswerController(
            PortfolioAgentRuntime runtime,
            AnswerResponseMapper responseMapper
    ) {
        this.runtime = runtime;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        AnswerResult result = runtime.answer(request);
        return responseMapper.toResponse(result);
    }
}
