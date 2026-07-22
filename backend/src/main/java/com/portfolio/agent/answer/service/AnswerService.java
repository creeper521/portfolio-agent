package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final PortfolioAgentRuntime runtime;

    public AnswerService(PortfolioAgentRuntime runtime) {
        this.runtime = runtime;
    }

    public AnswerResult answer(AnswerRequest request) {
        return runtime.answer(request);
    }
}
