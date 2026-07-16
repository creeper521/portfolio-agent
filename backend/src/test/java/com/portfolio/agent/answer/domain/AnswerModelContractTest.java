package com.portfolio.agent.answer.domain;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerModelContractTest {

    @Test
    void answerResultDefensivelyCopiesCollectionsAndKeepsValueSemantics() {
        List<AnswerSection> sections = new ArrayList<>(List.of(
                new AnswerSection(AnswerSectionType.BACKGROUND, "背景")
        ));
        AnswerResult first = new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题",
                sections, List.of(), List.of("推荐问题")
        );
        AnswerResult second = new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题",
                List.of(new AnswerSection(AnswerSectionType.BACKGROUND, "背景")),
                List.of(), List.of("推荐问题")
        );

        sections.clear();

        assertThat(first.getSections()).hasSize(1);
        assertThatThrownBy(() -> first.getSections().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void answerResultDefensivelyCopiesEvidenceAndSuggestedQuestions() {
        List<AnswerEvidence> evidence = new ArrayList<>();
        List<String> suggestedQuestions = new ArrayList<>(List.of("推荐问题"));
        AnswerResult result = new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题",
                List.of(), evidence, suggestedQuestions
        );

        evidence.add(null);
        suggestedQuestions.clear();

        assertThat(result.getEvidence()).isEmpty();
        assertThat(result.getSuggestedQuestions()).containsExactly("推荐问题");
        assertThatThrownBy(() -> result.getEvidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getSuggestedQuestions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void answerSectionExposesFieldsAndKeepsValueSemantics() {
        AnswerSection first = new AnswerSection(AnswerSectionType.SOLUTION, "方案");
        AnswerSection second = new AnswerSection(AnswerSectionType.SOLUTION, "方案");

        assertThat(first.getType()).isEqualTo(AnswerSectionType.SOLUTION);
        assertThat(first.getContent()).isEqualTo("方案");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("SOLUTION", "方案");
        assertThat(first).isNotEqualTo(new AnswerSection(AnswerSectionType.STATUS, "方案"));
        assertThat(first).isNotEqualTo(new AnswerSection(AnswerSectionType.SOLUTION, "其他"));
    }

    @Test
    void answerResultExposesFieldsAndUsesEveryFieldForValueSemantics() {
        List<AnswerSection> sections = List.of(
                new AnswerSection(AnswerSectionType.BACKGROUND, "背景")
        );
        AnswerResult baseline = new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题",
                sections, List.of(), List.of("推荐问题")
        );

        assertThat(baseline.getAnswerMode()).isEqualTo(AnswerMode.DETERMINISTIC);
        assertThat(baseline.isMatched()).isTrue();
        assertThat(baseline.isFallback()).isFalse();
        assertThat(baseline.getTitle()).isEqualTo("标题");
        assertThat(baseline.toString()).contains("DETERMINISTIC", "标题", "推荐问题");
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.MODEL, true, false, "标题", sections, List.of(), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, false, false, "标题", sections, List.of(), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, true, true, "标题", sections, List.of(), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "其他", sections, List.of(), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题", List.of(), List.of(), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题", sections,
                List.of(evidence("evidence-1")), List.of("推荐问题")));
        assertThat(baseline).isNotEqualTo(new AnswerResult(
                AnswerMode.DETERMINISTIC, true, false, "标题", sections, List.of(), List.of("其他")));
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id,
                "证据",
                "DOCUMENT",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                1,
                "摘要",
                List.of("事实"),
                "APPROVED",
                false
        );
    }
}
