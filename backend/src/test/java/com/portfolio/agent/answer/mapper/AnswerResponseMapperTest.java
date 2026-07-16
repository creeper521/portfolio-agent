package com.portfolio.agent.answer.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.dto.response.AnswerEvidenceResponse;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerResponseMapperTest {

    @Test
    void mapsAnswerResultToUnchangedJsonReadyResponseFields() throws Exception {
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
        assertThat(response.getEvidence().getFirst()).isInstanceOf(AnswerEvidenceResponse.class);
        assertThat(response.getEvidence().getFirst().getType()).isEqualTo("COLLECTION");
        assertThat(response.getEvidence().getFirst().getPublicStatus()).isEqualTo("APPROVED");
        assertThat(response.getEvidence().getFirst().isRawContentPublic()).isFalse();
        assertThat(response.getSuggestedQuestions()).containsExactly("What was the final status?");

        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
                """
                {"requestId":"request-1","answerMode":"DETERMINISTIC","matched":true,"fallback":false,"answer":{"title":"SQL audit","sections":[{"type":"BACKGROUND","content":"Background"}]},"evidence":[{"id":"evidence-1","title":"SQL audit evidence","type":"COLLECTION","periodStart":"2026-01-01","periodEnd":"2026-01-31","sourceCount":3,"summary":"Reviewed public evidence","supportedClaims":["The audit was completed"],"publicStatus":"APPROVED","rawContentPublic":false}],"suggestedQuestions":["What was the final status?"]}""");
    }
}
