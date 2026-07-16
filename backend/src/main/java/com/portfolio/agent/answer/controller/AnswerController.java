package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.mapper.AnswerResponseMapper;
import com.portfolio.agent.answer.service.AnswerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerService answerService;
    private final AnswerResponseMapper responseMapper;

    public AnswerController(
            AnswerService answerService,
            AnswerResponseMapper responseMapper
    ) {
        this.answerService = answerService;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        String requestId = UUID.randomUUID().toString();
        AnswerResult result = answerService.answer(
                request.getProjectSlug(),
                request.getQuestion()
        );
        return responseMapper.toResponse(requestId, result);
    }
}
