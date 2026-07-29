package com.portfolio.agent.answer.adapter.observability;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.QuestionKind;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class LoggingAnswerDecisionPublisherTest {

    @Test
    void publishesSafeV1DecisionFields() {
        List<DiagnosticEvent> events = new ArrayList<>();
        DiagnosticEventPublisher eventPublisher = events::add;
        LoggingAnswerDecisionPublisher publisher = new LoggingAnswerDecisionPublisher(eventPublisher);

        publisher.publish(decision());

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("agent.request.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.INFO);
            assertThat(event.getFields())
                    .hasSize(10)
                    .containsOnly(
                            entry("content.version", "2026-07-29.1"),
                            entry("question.kind", "FREE_TEXT"),
                            entry("audience.role", "GUEST"),
                            entry("request.source", "AGENT_PAGE"),
                            entry("answer.resolution", "ANSWERED"),
                            entry("answer.source", "PRESET"),
                            entry("generation.mode", "DETERMINISTIC"),
                            entry("verification.status", "VERIFIED"),
                            entry("duration.bucket", "LT_100_MS"),
                            entry("error.code", "NONE"));
        });
    }

    private AnswerDecision decision() {
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                "turn-1",
                "request-1",
                "2026-07-29.1",
                "sha256:runtime",
                "sql-audit",
                "overview",
                List.of("evidence-1"),
                AudienceRole.GUEST,
                AnswerRequestSource.AGENT_PAGE);
        AnswerResult result = new AnswerResult(
                turn,
                AnswerResolution.ANSWERED,
                AnswerSource.PRESET,
                GenerationMode.DETERMINISTIC,
                VerificationStatus.VERIFIED,
                "title",
                "summary",
                List.of(),
                List.of("evidence-1"),
                List.of());
        return new AnswerDecision(
                Instant.parse("2026-07-29T00:00:00Z"),
                turn,
                QuestionKind.FREE_TEXT,
                result,
                DurationBucket.LT_100_MS,
                "NONE");
    }
}
