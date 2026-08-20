package com.portfolio.agent.turn.capability.portfolio.knowledge;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerQuestionTest {

    @Test
    void carriesContractSubjectIdThroughFullConstructor() {
        AnswerQuestion question = new AnswerQuestion(
                "preset-a",
                "Canonical?",
                List.of("Alias"),
                "Suggestion",
                List.of(),
                "pcv1-0123456789abcdef",
                List.of("claim-1"),
                List.of("claim-2"),
                1,
                true,
                "subject-a");

        assertThat(question.getContractSubjectId()).isEqualTo("subject-a");
        assertThat(question.getId()).isEqualTo("preset-a");
        assertThat(question.isActiveContract()).isTrue();
    }

    @Test
    void legacyConstructorDefaultsContractSubjectIdToNull() {
        AnswerQuestion question = new AnswerQuestion(
                "preset-a", "Canonical?", List.of("Alias"), "Suggestion");

        assertThat(question.getContractSubjectId()).isNull();
    }
}
