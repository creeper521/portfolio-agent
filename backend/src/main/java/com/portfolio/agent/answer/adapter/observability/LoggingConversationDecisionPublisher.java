package com.portfolio.agent.answer.adapter.observability;

import com.portfolio.agent.answer.domain.ConversationDecision;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.springframework.stereotype.Component;

@Component
public final class LoggingConversationDecisionPublisher implements ConversationDecisionPublisher {

    private static final String NO_ANSWER_SOURCE = "NONE";

    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public LoggingConversationDecisionPublisher(DiagnosticEventPublisher diagnosticEventPublisher) {
        this.diagnosticEventPublisher = diagnosticEventPublisher;
    }

    @Override
    public void publish(ConversationDecision decision) {
        DiagnosticEvent event = DiagnosticEvent.builder(
                        "agent.request.completed", DiagnosticLevel.INFO)
                .field("content.version", decision.getContentVersion())
                .field("conversation.intent", decision.getIntent())
                .field("answer.scope", decision.getAnswerScope())
                .field("answer.resolution", decision.getResolution())
                .field("answer.degraded", decision.isDegraded())
                .field("generation.mode", decision.getGenerationMode())
                .field("answer.source", answerSource(decision))
                .field("duration.bucket", decision.getDurationBucket())
                .build();
        diagnosticEventPublisher.publish(event);
    }

    private String answerSource(ConversationDecision decision) {
        return decision.getAnswerSource() == null
                ? NO_ANSWER_SOURCE
                : decision.getAnswerSource().name();
    }
}
