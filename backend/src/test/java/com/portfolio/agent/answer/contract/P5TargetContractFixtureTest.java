package com.portfolio.agent.answer.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class P5TargetContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void freezesTheTargetFieldsBeforeTheBackendProducesNewSemantics() throws Exception {
        JsonNode root = readFixture("/evaluation/p5/p5-target-contract-v1.json");

        assertThat(root.path("contractVersion").asText()).isEqualTo("stp-v2");
        assertThat(root.path("agentTurn").path("plan").path("tasks"))
                .allMatch(task -> task.hasNonNull("fulfillmentRole"));
        assertThat(root.path("blocks")).allMatch(block -> block.hasNonNull("blockId")
                && block.has("support") && block.path("support").has("publicSourceKeys"));
        assertThat(root.path("sourceComposition").asText()).isEqualTo("MULTI_SOURCE");
        assertThat(root.path("publicSourceCatalog").isArray()).isTrue();
        assertThat(root.path("unsupportedContract409").path("httpStatus").asInt()).isEqualTo(409);
        assertThat(root.path("unsupportedContract409").path("errorCode").asText())
                .isEqualTo("AGENT_TURN_CONTRACT_UNSUPPORTED");
        assertThat(root.path("contextInvalidatedAnswer").path("httpStatus").asInt()).isEqualTo(200);
        assertThat(root.path("contextInvalidatedAnswer").path("disposition").asText())
                .isEqualTo("CONTEXT_INVALIDATED");
        assertThat(root.path("contextInvalidatedAnswer").has("contextResolution")).isFalse();
    }

    private JsonNode readFixture(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            assertThat(inputStream).as("fixture %s", path).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }
}
