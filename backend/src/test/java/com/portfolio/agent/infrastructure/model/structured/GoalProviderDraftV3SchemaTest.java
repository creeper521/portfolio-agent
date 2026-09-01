package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProviderDraftV3SchemaTest {

    private static final int SLOT_COUNT = 18;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsFixedFlatRecommendationAndDiscussionSlots() {
        assertThat(validate(flat("""
                "decision":"STANDARD_GOAL",
                "goalKind":"PORTFOLIO_RECOMMEND",
                "requestedSize":"2",
                "constraints":"[]"
                """))).isEmpty();
        assertThat(validate(flat("""
                "decision":"CONTINUE_CURRENT_PROJECT",
                "goalKind":"PORTFOLIO_FACT",
                "facets":"[\\\"SOLUTION\\\"]",
                "depth":"DETAILED"
                """))).isEmpty();
    }

    @Test
    void requiresEveryFlatSlotAndRejectsUnknownOrNestedGoalFields() {
        String valid = flat("""
                "decision":"STANDARD_GOAL",
                "goalKind":"PORTFOLIO_RECOMMEND",
                "requestedSize":2,
                "constraints":[]
                """);
        assertThat(validate(valid)).isEmpty();
        assertThat(validate(valid.replace(",\"recentSectionId\":null", "")))
                .isNotEmpty();
        assertThat(validate(valid.replace(
                "\"message\":null,", "\"message\":null,\"unexpected\":null,")))
                .isNotEmpty();
        assertThat(validate(valid.replace(
                "\"goalKind\":\"PORTFOLIO_RECOMMEND\"",
                "\"goalKind\":{\"value\":\"PORTFOLIO_RECOMMEND\"}")))
                .isNotEmpty();
    }

    @Test
    void schemaUsesOnlyDirectPropertiesAndAClosedRequiredSlotSet()
            throws Exception {
        JsonNode schema = schemaNode();

        assertThat(schema.path("required")).hasSize(SLOT_COUNT);
        assertThat(schema.path("properties").size()).isEqualTo(SLOT_COUNT);
        assertThat(schema.toString()).doesNotContain(
                "$defs", "$ref", "\"oneOf\"", "\"allOf\"",
                "\"if\"", "\"then\"");
        assertThat(schema.path("properties").has("goal")).isFalse();
        assertThat(schema.path("properties").path("constraints").path("anyOf"))
                .hasSize(3);
        assertThat(schema.toString().length()).isLessThan(4_000);
    }

    private String flat(String selectedFields) {
        java.util.LinkedHashMap<String, String> slots = new java.util.LinkedHashMap<>();
        slots.put("decision", null);
        slots.put("message", "null");
        slots.put("candidateKey", "null");
        slots.put("goalKind", "null");
        slots.put("subjects", "null");
        slots.put("facets", "null");
        slots.put("depth", "null");
        slots.put("dimensions", "null");
        slots.put("requestedSize", "null");
        slots.put("constraints", "null");
        slots.put("topicText", "null");
        slots.put("subjectTexts", "null");
        slots.put("conceptText", "null");
        slots.put("portfolioFacet", "null");
        slots.put("clarificationField", "null");
        slots.put("clarificationPrompt", "null");
        slots.put("recentGoalId", "null");
        slots.put("recentSectionId", "null");
        try {
            JsonNode selected = mapper.readTree("{" + selectedFields + "}");
            selected.fields().forEachRemaining(entry ->
                    slots.put(entry.getKey(), entry.getValue().toString()));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        return slots.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private List<Error> validate(String payload) {
        try {
            Schema schema = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode());
            return schema.validate(mapper.readTree(payload));
        } catch (Exception failure) {
            throw new AssertionError("cannot load or validate v3 schema", failure);
        }
    }

    private JsonNode schemaNode() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/model-contracts/goal.provider-draft.v3.schema.json")) {
            assertThat(input).as("v3 schema resource").isNotNull();
            return mapper.readTree(input);
        }
    }
}
