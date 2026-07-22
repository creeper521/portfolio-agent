package com.portfolio.agent.answer.domain;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerModelContractTest {

    @Test
    void answerResultDefensivelyCopiesStructuredCollections() {
        List<AnswerSection> sections = new ArrayList<>(List.of(
                new AnswerSection(
                        AnswerSectionType.BACKGROUND,
                        "项目背景",
                        "背景",
                        List.of("evidence-1")
                )
        ));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        List<String> suggestions = new ArrayList<>(List.of("preset-1"));
        AnswerResult result = new AnswerResult(
                turn(), AnswerResolution.ANSWERED, AnswerSource.PRESET,
                GenerationMode.DETERMINISTIC, VerificationStatus.VERIFIED,
                "标题", "摘要", sections, evidenceIds, suggestions
        );

        sections.clear();
        evidenceIds.clear();
        suggestions.clear();

        assertThat(result.getSections()).hasSize(1);
        assertThat(result.getEvidenceIds()).containsExactly("evidence-1");
        assertThat(result.getSuggestedQuestionPresetIds()).containsExactly("preset-1");
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getAnswerSource()).isEqualTo(AnswerSource.PRESET);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(result.getVerification()).isEqualTo(VerificationStatus.VERIFIED);
        assertThatThrownBy(() -> result.getSections().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getEvidenceIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getSuggestedQuestionPresetIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void answerSectionPreservesLocalEvidenceReferences() {
        AnswerSection first = new AnswerSection(
                AnswerSectionType.SOLUTION,
                "技术方案",
                "方案",
                List.of("evidence-1"),
                List.of("claim-1")
        );

        assertThat(first.getType()).isEqualTo(AnswerSectionType.SOLUTION);
        assertThat(first.getTitle()).isEqualTo("技术方案");
        assertThat(first.getContent()).isEqualTo("方案");
        assertThat(first.getEvidenceIds()).containsExactly("evidence-1");
        assertThat(first.getClaimIds()).containsExactly("claim-1");
        assertThatThrownBy(() -> first.getEvidenceIds().add("evidence-2"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.getClaimIds().add("claim-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AnswerTurnSnapshot turn() {
        return new AnswerTurnSnapshot(
                "turn-1",
                "request-1",
                "version-1",
                "sha256:bundle",
                "sql-audit",
                "preset-1",
                List.of("evidence-1"),
                com.portfolio.agent.answer.dto.request.AudienceRole.INTERVIEWER,
                com.portfolio.agent.answer.dto.request.AnswerRequestSource.AGENT_PAGE
        );
    }
}
