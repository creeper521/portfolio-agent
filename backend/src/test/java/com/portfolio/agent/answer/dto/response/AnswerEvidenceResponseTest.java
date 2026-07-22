package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class AnswerEvidenceResponseTest {

    @Test
    void exposesImmutableJsonReadyEvidenceValue() throws Exception {
        AnswerEvidenceResponse response = response();

        assertThat(response.getId()).isEqualTo("evidence-1");
        assertThat(response.getTitle()).isEqualTo("SQL audit evidence");
        assertThat(response.getType()).isEqualTo("COLLECTION");
        assertThat(response.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(response.getSourceCount()).isEqualTo(3);
        assertThat(response.getSummary()).isEqualTo("Reviewed public evidence");
        assertThat(response.getPublicStatus()).isEqualTo("APPROVED");
        assertThat(response.isRawContentPublic()).isFalse();

        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
                """
                {"id":"evidence-1","title":"SQL audit evidence","type":"COLLECTION","periodStart":"2026-01-01","periodEnd":"2026-01-31","sourceCount":3,"summary":"Reviewed public evidence","publicStatus":"APPROVED","rawContentPublic":false}""");
    }

    @Test
    void implementsValueEqualityHashCodeAndReadableString() {
        AnswerEvidenceResponse response = response();
        AnswerEvidenceResponse equalResponse = response();
        AnswerEvidenceResponse differentResponse = new AnswerEvidenceResponse(
                "evidence-2",
                "SQL audit evidence",
                "COLLECTION",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                3,
                "Reviewed public evidence",
                "APPROVED",
                false
        );

        assertThat(response).isEqualTo(equalResponse);
        assertThat(response.hashCode()).isEqualTo(equalResponse.hashCode());
        assertThat(response).isNotEqualTo(differentResponse);
        assertThat(response.toString()).contains(
                "AnswerEvidenceResponse",
                "evidence-1",
                "COLLECTION",
                "APPROVED",
                "rawContentPublic=false"
        );
    }

    private AnswerEvidenceResponse response() {
        return new AnswerEvidenceResponse(
                "evidence-1",
                "SQL audit evidence",
                "COLLECTION",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                3,
                "Reviewed public evidence",
                "APPROVED",
                false
        );
    }
}
