package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.dto.request.ConversationMessageRequest;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationWindowManagerTest {

    @Test
    void keepsAllHistoryWhenItFitsTheInputBudget() {
        RecordingSummaryPort summaryPort = new RecordingSummaryPort(Optional.of("unused"));
        ConversationWindowManager manager =
                new ConversationWindowManager(summaryPort, 1_000, 6);

        ConversationWindow window = manager.prepare(history(3), "current question");

        assertThat(window.getSummary()).isEmpty();
        assertThat(window.getRecentMessages()).hasSize(6);
        assertThat(window.getRecentMessages().get(0).getContent()).isEqualTo("question-1");
        assertThat(summaryPort.getReceived()).isEmpty();
    }

    @Test
    void summarizesOlderRoundsAndKeepsSixRecentRoundsWhenBudgetIsExceeded() {
        RecordingSummaryPort summaryPort =
                new RecordingSummaryPort(Optional.of("summary of older rounds"));
        ConversationWindowManager manager =
                new ConversationWindowManager(summaryPort, 20, 6);

        ConversationWindow window = manager.prepare(history(20), "current question");

        assertThat(summaryPort.getReceived()).hasSize(28);
        assertThat(summaryPort.getReceived().get(0).getContent()).isEqualTo("question-1");
        assertThat(summaryPort.getReceived().get(27).getContent()).isEqualTo("answer-14");
        assertThat(window.getSummary()).contains("summary of older rounds");
        assertThat(window.getRecentMessages()).hasSize(12);
        assertThat(window.getRecentMessages().get(0).getContent()).isEqualTo("question-15");
        assertThat(window.getRecentMessages().get(11).getContent()).isEqualTo("answer-20");
    }

    @Test
    void fallsBackToRecentRawRoundsWhenSummaryGenerationFails() {
        RecordingSummaryPort summaryPort = new RecordingSummaryPort(Optional.empty());
        ConversationWindowManager manager =
                new ConversationWindowManager(summaryPort, 20, 6);

        ConversationWindow window = manager.prepare(history(20), "current question");

        assertThat(window.getSummary()).isEmpty();
        assertThat(window.getRecentMessages()).hasSize(12);
        assertThat(window.getRecentMessages().get(0).getContent()).isEqualTo("question-15");
    }

    @Test
    void estimatesCjkConservativelyAndAsciiByFourCharactersPerToken() {
        ConversationWindowManager manager =
                new ConversationWindowManager(messages -> Optional.empty(), 100, 6);

        assertThat(manager.estimateTokens("中文A")).isEqualTo(3);
        assertThat(manager.estimateTokens("abcdef")).isEqualTo(2);
        assertThat(manager.estimateTokens("")).isZero();
    }

    private List<ConversationMessageRequest> history(int rounds) {
        List<ConversationMessageRequest> messages = new ArrayList<>();
        for (int index = 1; index <= rounds; index++) {
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.USER, "question-" + index));
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.ASSISTANT, "answer-" + index));
        }
        return messages;
    }

    private static final class RecordingSummaryPort implements ConversationSummaryPort {

        private final Optional<String> result;
        private List<ConversationMessage> received = List.of();

        private RecordingSummaryPort(Optional<String> result) {
            this.result = result;
        }

        @Override
        public Optional<String> summarize(List<ConversationMessage> messages) {
            received = List.copyOf(messages);
            return result;
        }

        private List<ConversationMessage> getReceived() {
            return received;
        }
    }
}
