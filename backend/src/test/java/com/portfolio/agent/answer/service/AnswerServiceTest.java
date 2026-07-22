package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.exception.AnswerErrorCode;
import com.portfolio.agent.answer.exception.AnswerProjectNotFoundException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerServiceTest {

    @Test
    void delegatesTheTypedRequestToPortfolioAgentRuntime() {
        PortfolioAgentRuntime runtime = org.mockito.Mockito.mock(PortfolioAgentRuntime.class);
        com.portfolio.agent.answer.dto.request.AnswerRequest request =
                org.mockito.Mockito.mock(com.portfolio.agent.answer.dto.request.AnswerRequest.class);
        AnswerResult expected = org.mockito.Mockito.mock(AnswerResult.class);
        org.mockito.Mockito.when(runtime.answer(request)).thenReturn(expected);
        AnswerService service = new AnswerService(runtime);

        AnswerResult result = service.answer(request);

        assertThat(result).isSameAs(expected);
        org.mockito.Mockito.verify(runtime).answer(request);
    }
}
