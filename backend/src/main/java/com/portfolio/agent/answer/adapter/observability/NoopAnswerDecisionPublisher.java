package com.portfolio.agent.answer.adapter.observability;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import org.springframework.stereotype.Component;

@Component
public final class NoopAnswerDecisionPublisher implements AnswerDecisionPublisher {

    @Override
    public void publish(AnswerDecision decision) {
        // Intentionally passive: A has no visitor telemetry sink by default.
    }
}
