package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
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
 * Qwen v4 的采样温度固定为 0.0；identity/GLM 保留既有 0.2。只有 v4
 * General Draft binding 进入同模型、共享 deadline 的有界 transport retry，
 * identity/GLM 路径仍为单次调用。</p>
 */
public final class OpenAiCompatibleGeneralKnowledgeAdapter implements GeneralKnowledgeModelPort {
    private final StructuredOutputGateway gateway;
    private final ObjectMapper mapper;
    private final String systemPrompt;
    private final String providerDraftSystemPrompt;
    private final int maxTokens;
    private final Duration timeout;
    private final ModelOutputDiagnostics outputDiagnostics;
    private final GeneralTransportRetryExecutor retryExecutor;
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredOutputGateway gateway, ObjectMapper mapper,
            String systemPrompt, int maxTokens, Duration timeout) {
        this(gateway, mapper, systemPrompt, maxTokens, timeout,
                systemPrompt, ModelOutputDiagnostics.none());
    }
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredOutputGateway gateway, ObjectMapper mapper,
            String systemPrompt, int maxTokens, Duration timeout,
            ModelOutputDiagnostics outputDiagnostics) {
        this(gateway, mapper, systemPrompt, maxTokens, timeout,
                systemPrompt, outputDiagnostics);
    }
    public OpenAiCompatibleGeneralKnowledgeAdapter(
            StructuredOutputGateway gateway, ObjectMapper mapper,
            String systemPrompt, int maxTokens, Duration timeout,
            String providerDraftSystemPrompt,
            ModelOutputDiagnostics outputDiagnostics) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.mapper = mapper;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        if (providerDraftSystemPrompt == null
                || providerDraftSystemPrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "providerDraftSystemPrompt is required");
        }
        this.systemPrompt = systemPrompt;
        this.providerDraftSystemPrompt = providerDraftSystemPrompt;
        this.maxTokens = maxTokens; this.timeout = timeout;
        this.outputDiagnostics = java.util.Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
        this.retryExecutor = new GeneralTransportRetryExecutor(
                gateway, outputDiagnostics);
    }
    /**
     * 执行一次通用知识生成调用，返回模型输出的 JSON 草稿文本。
     *
     * @throws SelectedModelFailureException 所选模型不支持该能力、被限流或传输失败
     * @throws GeneralKnowledgeUnavailableException 能力未启用或输入投影失败
     */
    @Override public StructurallyValidatedOutput generate(
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
            input.put("subjects", request.getSubjects());
            input.put("dimensions", request.getDimensions().stream().sorted().toList());
            input.put("depth", request.getDepth()); input.put("audience", request.getAudience());
            input.put("expectedContentVersion", request.getExpectedContentVersion());
            OperationBinding binding = modelExecution.getRequiredBinding()
                    .getRequiredOperationBinding(
                            com.portfolio.agent.infrastructure.model.policy
                                    .ModelOperation.GENERAL_KNOWLEDGE);
            String compilerProfile = binding.getOutputCompilerProfileVersion();
            String selectedSystemPrompt = switch (compilerProfile) {
                case OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION -> systemPrompt;
                case OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION ->
                        providerDraftSystemPrompt;
                default -> throw new IllegalArgumentException(
                        "general output compiler profile is not approved");
            };
            double temperature = OperationBinding
                    .GENERAL_DRAFT_OUTPUT_COMPILER_VERSION
                    .equals(compilerProfile) ? 0.0d : 0.2d;
            StructuredModelRequest modelRequest = new StructuredModelRequest(
                    com.portfolio.agent.infrastructure.model.policy.ModelOperation
                            .GENERAL_KNOWLEDGE,
                    selectedSystemPrompt, mapper.writeValueAsString(input),
                    maxTokens, temperature,
                    request.getDeadline().cappedAt(timeout));
            if (modelRequest.deadline().isExpired()) {
                throw SelectedModelFailureException
                        .temporarilyUnavailableBeforeAttempt();
            }
            modelExecution.markAttempted(
                    ResolvedModelExecution.Stage.ANSWER_GENERATION);
            StructuredOutputCompiler compiler = switch (compilerProfile) {
                case OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION ->
                        StructuredOutputCompiler.identity();
                case OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION ->
                        new GeneralProviderDraftCompiler(
                                request, outputDiagnostics);
                default -> throw new IllegalArgumentException(
                        "general output compiler profile is not approved");
            };
            if (OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION.equals(
                    compiler.profileVersion())) {
                return retryExecutor.execute(
                        modelExecution.getRequiredBinding(),
                        modelRequest, compiler);
            }
            return gateway.execute(
                    modelExecution.getRequiredBinding(), modelRequest, compiler);
        } catch (StructuredModelFailure failure) {
            throw SelectedModelFailureException.from(failure);
        } catch (StructuredOutputValidationException failure) {
            outputDiagnostics.rejected(
                    "GENERAL_KNOWLEDGE", diagnosticLayer(failure),
                    failure.getDiagnosticReason());
            throw SelectedModelFailureException.invalidResponse(failure);
        } catch (SelectedModelFailureException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new GeneralKnowledgeUnavailableException("general request projection failed", failure);
        }
    }

    private ModelOutputDiagnostics.Layer diagnosticLayer(
            StructuredOutputValidationException failure) {
        return switch (failure.getStage()) {
            case PROVIDER_DRAFT_SCHEMA ->
                    ModelOutputDiagnostics.Layer.PROVIDER_DRAFT_SCHEMA;
            case DETERMINISTIC_COMPILER ->
                    ModelOutputDiagnostics.Layer.DETERMINISTIC_COMPILER;
            case CANONICAL_SCHEMA ->
                    ModelOutputDiagnostics.Layer.CANONICAL_SCHEMA;
            case UNCLASSIFIED_SCHEMA -> ModelOutputDiagnostics.Layer.SCHEMA;
        };
    }
}
