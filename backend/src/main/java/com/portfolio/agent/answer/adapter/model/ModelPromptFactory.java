package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;

final class ModelPromptFactory {

    private static final String SYSTEM_PROMPT = """
            Express only the approved public facts in the supplied JSON AnswerPlan.
            Return one JSON object with exactly: title, summary, sections.
            Each section must contain exactly: type, title, content, evidenceIds, claimIds.
            Preserve every required section type and use only supplied claim/evidence IDs.
            Never add facts, numbers, URLs, markdown, HTML, tools, actions, verification,
            resolution, answerSource, generationMode, or fields outside this schema.
            Use 【个人陈述】 for SELF_DECLARED claims and 【推断】 for INFERRED claims.
            """;

    private final ObjectMapper objectMapper;

    ModelPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String systemPrompt(String answerSchemaVersion) {
        return SYSTEM_PROMPT + "\nSchema version: " + answerSchemaVersion;
    }

    String planPrompt(ModelExpressionRequest request) {
        try {
            return "AnswerPlan JSON:\n" + objectMapper.writeValueAsString(
                    new ProviderAnswerPlanPayload(request.getAnswerPlan()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize provider-safe AnswerPlan");
        }
    }
}
