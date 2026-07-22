package com.portfolio.agent.answer.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerRequestTest {

    @Test
    void toStringRedactsVisitorQuestion() {
        String visitorQuestion = "unique-visitor-secret-question";
        AnswerRequest request = new AnswerRequest(
                "turn-1",
                null,
                visitorQuestion,
                new AnswerContextRequest(
                        "sql-audit",
                        AudienceRole.GUEST,
                        List.of(),
                        AnswerRequestSource.AGENT_PAGE
                )
        );

        assertThat(request.toString())
                .contains("<redacted>")
                .doesNotContain(visitorQuestion);
    }
}
