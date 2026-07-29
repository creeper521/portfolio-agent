package com.portfolio.agent.answer.adapter.observability;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.springframework.stereotype.Component;

@Component
public final class LoggingAnswerDecisionPublisher implements AnswerDecisionPublisher {

    private static final String NO_ERROR_CODE = "NONE";
    private static final String NO_ANSWER_SOURCE = "NONE";

    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public LoggingAnswerDecisionPublisher(DiagnosticEventPublisher diagnosticEventPublisher) {
        this.diagnosticEventPublisher = diagnosticEventPublisher;
    }

    @Override
    public void publish(AnswerDecision decision) {
        DiagnosticEvent event = DiagnosticEvent.builder(
                        "agent.request.completed", DiagnosticLevel.INFO)
                .field("content.version", decision.getContentVersion())
                .field("question.kind", decision.getQuestionKind())
                .field("audience.role", decision.getAudienceRole())
                .field("request.source", decision.getSource())
                .field("answer.resolution", decision.getResolution())
                .field("answer.source", answerSource(decision))
                .field("generation.mode", decision.getGenerationMode())
                .field("verification.status", decision.getVerification())
                .field("duration.bucket", decision.getDurationBucket())
                .field("error.code", errorCode(decision))
                .build();
        diagnosticEventPublisher.publish(event);
    }

    private String answerSource(AnswerDecision decision) {
        return decision.getAnswerSource() == null
                ? NO_ANSWER_SOURCE
                : decision.getAnswerSource().name();
    }

    private String errorCode(AnswerDecision decision) {
        return decision.getErrorCode() == null ? NO_ERROR_CODE : decision.getErrorCode();
    }
}
