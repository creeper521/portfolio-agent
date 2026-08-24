package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleGeneralKnowledgeAdapter implements GeneralKnowledgeModelPort {
    private final StructuredModelTransport transport;
    private final ObjectMapper mapper;
    private final String systemPrompt;
    private final int maxTokens;
    private final Duration timeout;
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            String systemPrompt, int maxTokens, Duration timeout) {
        this.transport = transport; this.mapper = mapper;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens; this.timeout = timeout;
    }
    @Override public String generate(
            GeneralKnowledgeRequest request,
            ResolvedModelExecution modelExecution) {
        if (!modelExecution.getSnapshot().supports(
                ModelCapability.GENERAL_KNOWLEDGE)) {
            if (modelExecution.getSnapshot().getKind()
                    == com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException.unavailableBeforeAttempt();
            }
            throw new GeneralKnowledgeUnavailableException(
                    "general capability is unavailable");
        }
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("kind", request.getKind()); input.put("topic", request.getTopic());
            input.put("subjects", request.getSubjects()); input.put("dimensions", request.getDimensions());
            input.put("depth", request.getDepth()); input.put("audience", request.getAudience());
            input.put("expectedContentVersion", request.getExpectedContentVersion());
            StructuredModelRequest modelRequest = new StructuredModelRequest(
                    "GENERAL_KNOWLEDGE", systemPrompt, mapper.writeValueAsString(input),
                    maxTokens, 0.2d, request.getDeadline().cappedAt(timeout));
            if (modelRequest.deadline().isExpired()) {
                throw SelectedModelFailureException
                        .temporarilyUnavailableBeforeAttempt();
            }
            modelExecution.markAttempted(
                    ResolvedModelExecution.Stage.ANSWER_GENERATION);
            return transport.execute(
                    modelExecution.getRequiredBinding(), modelRequest).json();
        } catch (StructuredModelFailure failure) {
            throw SelectedModelFailureException.from(failure);
        } catch (SelectedModelFailureException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new GeneralKnowledgeUnavailableException("general request projection failed", failure);
        }
    }
}
