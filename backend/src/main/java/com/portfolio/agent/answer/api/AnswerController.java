package com.portfolio.agent.answer.api;

import com.portfolio.agent.answer.api.dto.AnswerRequest;
import com.portfolio.agent.answer.api.dto.AnswerResponse;
import com.portfolio.agent.answer.application.AnswerService;
import com.portfolio.agent.answer.domain.model.AnswerResult;
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

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        String requestId = UUID.randomUUID().toString();
        AnswerResult result = answerService.answer(request.getProjectSlug(), request.getQuestion());
        return AnswerResponse.from(requestId, result);
    }
}
