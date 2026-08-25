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

/**
 * 通用知识生成的模型端口适配器（OpenAI 兼容结构化协议）。
 *
 * <p>把 GeneralKnowledgeRequest 投影为受限 JSON 输入，经结构化传输调用所选模型并
 * 返回 JSON 草稿文本；能力闸门、截止时间封顶与失败映射与 Goal 解析适配器一致。
 * 采样温度固定为 0.2（贴近确定的说明文风）。</p>
 */
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
    /**
     * 执行一次通用知识生成调用，返回模型输出的 JSON 草稿文本。
     *
     * @throws SelectedModelFailureException 所选模型不支持该能力、被限流或传输失败
     * @throws GeneralKnowledgeUnavailableException 能力未启用或输入投影失败
     */
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
