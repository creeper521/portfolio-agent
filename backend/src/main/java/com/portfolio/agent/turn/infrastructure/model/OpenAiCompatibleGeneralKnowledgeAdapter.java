package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleGeneralKnowledgeAdapter implements GeneralKnowledgeModelPort {
    private static final String SYSTEM_PROMPT = "Return only one JSON object. Root fields must be topic, statements, caveats. Each statement has role, text and optional subject/dimension. Explanation requires DEFINITION and MECHANISM. Comparison uses COMPARISON.";
    private final StructuredModelTransport transport;
    private final ObjectMapper mapper;
    private final int maxTokens;
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredModelTransport transport, ObjectMapper mapper, int maxTokens) {
        this.transport = transport; this.mapper = mapper; this.maxTokens = maxTokens;
    }
    @Override public String generate(GeneralKnowledgeRequest request) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("kind", request.getKind()); input.put("topic", request.getTopic());
            input.put("subjects", request.getSubjects()); input.put("dimensions", request.getDimensions());
            input.put("depth", request.getDepth()); input.put("audience", request.getAudience());
            input.put("expectedContentVersion", request.getExpectedContentVersion());
            return transport.execute(new StructuredModelRequest(
                    "GENERAL_KNOWLEDGE", SYSTEM_PROMPT, mapper.writeValueAsString(input),
                    maxTokens, 0.2d, request.getDeadline())).json();
        } catch (StructuredModelFailure failure) {
            throw new GeneralKnowledgeUnavailableException("general provider unavailable", failure);
        } catch (Exception failure) {
            throw new GeneralKnowledgeUnavailableException("general request projection failed", failure);
        }
    }
}
