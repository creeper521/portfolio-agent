package com.portfolio.agent.answer.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class P5CurrentContractCharacterizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordsTheCurrentStpV1AndLegacyBlockShape() throws Exception {
        JsonNode root = readFixture("/evaluation/p5/p5-current-characterization.json");

        assertThat(root.path("contractVersion").asText()).isEqualTo("stp-v1");
        assertThat(root.path("agentTurn").path("contractVersion").asText()).isEqualTo("stp-v1");
        assertThat(root.path("block").path("sourceScope").asText()).isEqualTo("PORTFOLIO");
        assertThat(root.path("block").path("sourceReferences").isArray()).isTrue();
        assertThat(root.path("block").path("support").isMissingNode()).isTrue();
    }

    private JsonNode readFixture(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            assertThat(inputStream).as("fixture %s", path).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }
}
