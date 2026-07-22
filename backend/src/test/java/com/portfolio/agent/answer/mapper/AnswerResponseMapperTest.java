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
    void mapsTheFourDimensionalStructuredContractWithoutLegacyFields() throws Exception {
        com.portfolio.agent.answer.domain.AnswerTurnSnapshot turn =
                new com.portfolio.agent.answer.domain.AnswerTurnSnapshot(
                        "turn-1", "request-1", "version-1", "sha256:bundle",
                        "sql-audit", "preset-1", List.of("evidence-1"),
                        com.portfolio.agent.answer.dto.request.AudienceRole.INTERVIEWER,
                        com.portfolio.agent.answer.dto.request.AnswerRequestSource.AGENT_PAGE
                );
        AnswerResult result = new AnswerResult(
                turn,
                com.portfolio.agent.answer.domain.AnswerResolution.ANSWERED,
                com.portfolio.agent.answer.domain.AnswerSource.PRESET,
                com.portfolio.agent.answer.domain.GenerationMode.DETERMINISTIC,
                com.portfolio.agent.answer.domain.VerificationStatus.VERIFIED,
                "SQL audit",
                "Reviewed summary",
                List.of(new AnswerSection(
                        AnswerSectionType.BACKGROUND,
                        "Background",
                        "Reviewed content",
                        List.of("evidence-1")
                )),
                List.of("evidence-1"),
                List.of("preset-1")
        );
        AnswerResponse response = new AnswerResponseMapper().toResponse(result);

        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getTurnId()).isEqualTo("turn-1");
        assertThat(response.getResolution())
                .isEqualTo(com.portfolio.agent.answer.domain.AnswerResolution.ANSWERED);
        assertThat(response.getSections()).singleElement()
                .satisfies(section -> assertThat(section.getEvidenceIds())
                        .containsExactly("evidence-1"));

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).contains("\"answerSource\":\"PRESET\"")
                .contains("\"generationMode\":\"DETERMINISTIC\"")
                .contains("\"verification\":\"VERIFIED\"")
                .doesNotContain("\"matched\"")
                .doesNotContain("\"answerMode\"")
                .doesNotContain("\"fallback\"");
    }
}
