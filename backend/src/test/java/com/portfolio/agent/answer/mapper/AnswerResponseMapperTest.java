package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerResponseMapperTest {

    @Test
    void mapsAnswerResultToJsonReadyResponseFields() {
        AnswerEvidence evidence = new AnswerEvidence(
                "evidence-1",
                "SQL audit evidence",
                "COLLECTION",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                3,
                "Reviewed public evidence",
                List.of("The audit was completed"),
                "APPROVED",
                false
        );
        AnswerResult result = new AnswerResult(
                AnswerMode.DETERMINISTIC,
                true,
                false,
                "SQL audit",
                List.of(new AnswerSection(AnswerSectionType.BACKGROUND, "Background")),
                List.of(evidence),
                List.of("What was the final status?")
        );
        AnswerResponseMapper mapper = new AnswerResponseMapper();

        AnswerResponse response = mapper.toResponse("request-1", result);

        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getAnswerMode()).isEqualTo(AnswerMode.DETERMINISTIC);
        assertThat(response.isMatched()).isTrue();
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getAnswer().getTitle()).isEqualTo("SQL audit");
        assertThat(response.getAnswer().getSections()).hasSize(1);
        assertThat(response.getEvidence()).hasSize(1);
        assertThat(response.getEvidence().getFirst().getType()).isEqualTo("COLLECTION");
        assertThat(response.getEvidence().getFirst().getPublicStatus()).isEqualTo("APPROVED");
        assertThat(response.getEvidence().getFirst().isRawContentPublic()).isFalse();
        assertThat(response.getSuggestedQuestions()).containsExactly("What was the final status?");
    }
}
