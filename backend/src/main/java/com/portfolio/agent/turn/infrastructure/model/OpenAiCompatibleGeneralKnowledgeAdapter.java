package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleGeneralKnowledgeAdapter implements GeneralKnowledgeModelPort {
    private static final String SYSTEM_PROMPT = """
            Return exactly one JSON object with no Markdown and no unknown fields.
            Root fields are topic, statements, caveats.
            For EXPLANATION, topic must exactly equal the requested topic and statements must
            contain at least one {"role":"DEFINITION","text":"..."} and one
            {"role":"MECHANISM","text":"..."}; do not include subject or dimension.
            For COMPARISON, every statement is {"role":"COMPARISON","text":"...",
            "subject":"exact requested subject","dimension":"exact requested dimension"}
            and every subject/dimension pair must be covered. caveats is an array of plain strings.
            Do not claim current facts, high-risk advice, portfolio evidence, citations, IDs or URLs.
            """;
    private final StructuredModelTransport transport;
    private final ObjectMapper mapper;
    private final int maxTokens;
    private final Duration timeout;
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            int maxTokens, Duration timeout) {
        this.transport = transport; this.mapper = mapper;
        this.maxTokens = maxTokens; this.timeout = timeout;
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
                    maxTokens, 0.2d, request.getDeadline().cappedAt(timeout))).json();
        } catch (StructuredModelFailure failure) {
            throw new GeneralKnowledgeUnavailableException("general provider unavailable", failure);
        } catch (Exception failure) {
            throw new GeneralKnowledgeUnavailableException("general request projection failed", failure);
        }
    }
}
