package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresetContractSetHashTest {

    @Test
    void isOrderIndependentByPresetId() {
        QuestionDefinition questionA = activeQuestion("preset-a", "subject-a");
        QuestionDefinition questionB = activeQuestion("preset-b", "subject-b");

        assertThat(PresetContractSetHash.calculate(List.of(questionB, questionA)))
                .isEqualTo(PresetContractSetHash.calculate(List.of(questionA, questionB)));
    }

    @Test
    void changesWhenAnyContractChanges() {
        QuestionDefinition questionA = activeQuestion("preset-a", "subject-a");
        QuestionDefinition questionAChanged = activeQuestion("preset-a", "subject-b");
        QuestionDefinition questionB = activeQuestion("preset-b", "subject-b");

        assertThat(PresetContractSetHash.calculate(List.of(questionA, questionB)))
                .isNotEqualTo(PresetContractSetHash.calculate(List.of(questionAChanged, questionB)));
    }

    @Test
    void rejectsDuplicateActivePresetIds() {
        QuestionDefinition questionA = activeQuestion("preset-a", "subject-a");
        QuestionDefinition duplicateA = activeQuestion("preset-a", "subject-a");

        assertThatThrownBy(() -> PresetContractSetHash.calculate(List.of(questionA, duplicateA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate active preset id");
    }

    @Test
    void rejectsNonActivePreset() {
        QuestionDefinition draft = new QuestionDefinition(
                "preset-a", "Canonical?", List.of("Alias"), List.of("INTERVIEWER"),
                List.of("subject-a"), List.of(), List.of("topic"),
                List.of(ClaimCategory.BACKGROUND), List.of("AGENT"), true, 0);

        assertThatThrownBy(() -> PresetContractSetHash.calculate(List.of(draft)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-active");
    }

    @Test
    void producesExpectedFormat() {
        QuestionDefinition questionA = activeQuestion("preset-a", "subject-a");

        assertThat(PresetContractSetHash.calculate(List.of(questionA)))
                .matches("sha256:[a-f0-9]{64}");
    }

    private static QuestionDefinition activeQuestion(String id, String subject) {
        return new QuestionDefinition(
                id, "Canonical?", List.of("Alias"), List.of("INTERVIEWER"),
                List.of(subject), List.of(), List.of("topic"),
                List.of(ClaimCategory.BACKGROUND), List.of("AGENT"), true, 0,
                subject, List.of("claim-1"), List.of(),
                new QuestionEvidenceRequirement(1, true), PresetContractStatus.ACTIVE);
    }
}
