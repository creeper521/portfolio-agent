package com.portfolio.agent.answer.adapter.observability;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDecision;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.gateway.ConversationDecisionPublisher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class LoggingConversationDecisionPublisherTest {

    @Test
    void publishesOnlySafeV2DecisionFields() {
        List<DiagnosticEvent> events = new ArrayList<>();
        DiagnosticEventPublisher eventPublisher = events::add;
        ConversationDecisionPublisher publisher =
                new LoggingConversationDecisionPublisher(eventPublisher);

        publisher.publish(new ConversationDecision(
                Instant.parse("2026-07-29T00:00:00Z"),
                "v2",
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                AnswerResolution.ANSWERED,
                false,
                GenerationMode.MODEL,
                AnswerSource.RETRIEVAL,
                DurationBucket.LT_100_MS));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("agent.request.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.INFO);
            assertThat(event.getFields()).containsExactly(
                    entry("content.version", "v2"),
                    entry("conversation.intent", "PORTFOLIO_GROUNDED"),
                    entry("answer.scope", "PORTFOLIO"),
                    entry("answer.resolution", "ANSWERED"),
                    entry("answer.degraded", false),
                    entry("generation.mode", "MODEL"),
                    entry("answer.source", "RETRIEVAL"),
                    entry("duration.bucket", "LT_100_MS"));
        });
    }

    @Test
    void publishesStableNoneWhenAnswerSourceIsAbsent() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ConversationDecisionPublisher publisher =
                new LoggingConversationDecisionPublisher(events::add);

        publisher.publish(new ConversationDecision(
                Instant.parse("2026-07-29T00:00:00Z"),
                "v2",
                ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION,
                AnswerResolution.ANSWERED,
                false,
                GenerationMode.DETERMINISTIC,
                null,
                DurationBucket.LT_100_MS));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields()).containsEntry("generation.mode", "DETERMINISTIC");
            assertThat(event.getFields()).containsEntry("answer.source", "NONE");
        });
    }
}
