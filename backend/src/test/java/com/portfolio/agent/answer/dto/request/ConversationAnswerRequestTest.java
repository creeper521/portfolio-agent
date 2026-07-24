package com.portfolio.agent.answer.dto.request;

import com.portfolio.agent.answer.domain.ConversationMessageRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsTwentyAlternatingConversationRounds() {
        ConversationAnswerRequest request = request(history(20));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMoreThanTwentyConversationRounds() {
        ConversationAnswerRequest request = request(history(21));

        Set<ConstraintViolation<ConversationAnswerRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("messages must contain at most 20 rounds");
    }

    @Test
    void rejectsNonAlternatingHistory() {
        ConversationAnswerRequest request = request(List.of(
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "first question"),
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "second question")));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("messages must alternate USER and ASSISTANT");
    }

    @Test
    void rejectsSimultaneousProjectAndCaseHints() {
        ConversationAnswerRequest request = new ConversationAnswerRequest(
                "turn-1",
                "visitor question",
                List.of(),
                new ConversationAnswerContextRequest(
                        "sql-audit",
                        "codegraph-evaluation",
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.AGENT_PAGE));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("projectSlug and caseSlug cannot both be set");
    }

    @Test
    void redactsVisitorTextFromDiagnostics() {
        ConversationAnswerRequest request = request(List.of(
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "history-secret"),
                new ConversationMessageRequest(
                        ConversationMessageRole.ASSISTANT, "answer-secret")));

        assertThat(request.toString())
                .doesNotContain("visitor question", "history-secret", "answer-secret")
                .contains("question='<redacted>'", "messageCount=2");
    }

    private ConversationAnswerRequest request(List<ConversationMessageRequest> messages) {
        return new ConversationAnswerRequest(
                "turn-1",
                "visitor question",
                messages,
                new ConversationAnswerContextRequest(
                        null,
                        null,
                        AudienceRole.GUEST,
                        AnswerRequestSource.AGENT_PAGE));
    }

    private List<ConversationMessageRequest> history(int rounds) {
        List<ConversationMessageRequest> messages = new ArrayList<>();
        for (int index = 0; index < rounds; index++) {
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.USER, "question-" + index));
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.ASSISTANT, "answer-" + index));
        }
        return messages;
    }
}
