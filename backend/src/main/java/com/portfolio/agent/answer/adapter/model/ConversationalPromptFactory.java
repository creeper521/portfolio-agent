package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConversationalPromptFactory {

    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public ConversationalPromptFactory(ObjectMapper objectMapper, String systemPrompt) {
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    public String systemPrompt(String operation) {
        return systemPrompt + "\n\n当前任务：" + operation;
    }

    public String intentPrompt(Object conversation, Object publicSubjects) {
        return prompt("intent", conversation, publicSubjects);
    }

    public String summaryPrompt(Object conversation) {
        return prompt("summary", conversation, null);
    }

    public String toolPlanPrompt(Object conversation, Object approvedContext) {
        return prompt("tool_plan", conversation, approvedContext);
    }

    public String generationPrompt(Object conversation, Object approvedContext) {
        return prompt("generation", conversation, approvedContext);
    }

    public String reviewPrompt(Object blocks, Object approvedContext) {
        return prompt("review", blocks, approvedContext);
    }

    public String suggestionPrompt(Object conversation, Object approvedContext) {
        return prompt("suggestion", conversation, approvedContext);
    }

    private String prompt(String operation, Object untrusted, Object approved) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("operation", operation);
        envelope.put("payload", untrusted);
        StringBuilder prompt = new StringBuilder();
        prompt.append("<untrusted_conversation>\n")
                .append(json(envelope))
                .append("\n</untrusted_conversation>");
        if (approved != null) {
            prompt.append("\n<approved_portfolio_context>\n")
                    .append(json(approved))
                    .append("\n</approved_portfolio_context>");
        }
        return prompt.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unable to serialize prompt payload", exception);
        }
    }
}
