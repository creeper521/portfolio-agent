package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;

import java.util.Objects;

/**
 * One logical generation, one strict decode and one semantic validation.
 *
 * <p>通用知识生成器：一次逻辑模型生成 + 一次严格解码 + 一次语义校验；端口内部可按
 * 获批闭集执行同模型 transport retry，但本层不做内容重试、repair 或 Provider 回退。
 * 解码失败记 SCHEMA 层诊断、语义校验失败记
 * SEMANTIC 层诊断，随后按执行快照类别转换：MODEL 快照抛
 * {@code SelectedModelFailureException}（整轮 Turn 终止），NONE 快照抛
 * {@link GeneralKnowledgeUnavailableException}（通用能力不可用）。
 * 校验通过后调用 {@code markAdopted(ANSWER_GENERATION)} 将产出标记为已采纳。
 */
public final class GeneralKnowledgeGenerator {
    private final GeneralKnowledgeModelPort modelPort;
    private final GeneralDraftCodec codec;
    private final GeneralDraftValidator validator;
    private final ModelOutputDiagnostics outputDiagnostics;

    public GeneralKnowledgeGenerator(
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator) {
        this(modelPort, codec, validator, ModelOutputDiagnostics.none());
    }

    public GeneralKnowledgeGenerator(
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator,
            ModelOutputDiagnostics outputDiagnostics) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.outputDiagnostics = Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }

    /**
     * 生成通用知识语义结果：逻辑模型生成 → 严格解码 → 语义校验；本层无重试。
     *
     * <p>进入前先检查截止时间：已过期时不发起调用，MODEL 快照抛"尝试前暂不可用"
     * 的模型失败，NONE 快照直接抛通用能力不可用。解码/校验失败按快照类别转换
     * （见类注释），并记录对应层的 ModelOutputDiagnostics 拒绝计数。
     *
     * @param modelExecution Claim 后冻结的无凭证执行快照；校验通过后标记 ANSWER_GENERATION 采纳
     * @throws SelectedModelFailureException MODEL 快照下任何失败或过期，整轮 fail-closed
     * @throws GeneralKnowledgeUnavailableException NONE 快照下的失败、过期或端口不可用
     */
    public GeneralSemanticResult generate(
            GeneralKnowledgeRequest request,
            ResolvedModelExecution modelExecution) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(modelExecution, "modelExecution");
        if (request.getDeadline().isExpired()) {
            if (modelExecution.getSnapshot().getKind()
                    == ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException
                        .temporarilyUnavailableBeforeAttempt();
            }
            throw new GeneralKnowledgeUnavailableException("general capability is unavailable");
        }
        try {
            com.portfolio.agent.infrastructure.model.structured
                    .StructurallyValidatedOutput output =
                    modelPort.generate(request, modelExecution);
            GeneralDraftCodec.Draft draft;
            try {
                draft = codec.decode(output);
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SCHEMA);
                throw invalidResponse(modelExecution, exception);
            }
            try {
                GeneralSemanticResult result = validator.validate(request, draft);
                modelExecution.markAdopted(
                        ResolvedModelExecution.Stage.ANSWER_GENERATION);
                return result;
            } catch (GeneralDraftValidationException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC,
                        exception.getReason().name());
                throw invalidResponse(modelExecution, exception);
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC);
                throw invalidResponse(modelExecution, exception);
            }
        } catch (SelectedModelFailureException exception) {
            throw exception;
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralKnowledgeUnavailableException("general generation failed", exception);
        }
    }

    /** 兼容无模型执行的旧入口：以 {@code ResolvedModelExecution.none()} 委托给完整入口。 */
    GeneralSemanticResult generate(GeneralKnowledgeRequest request) {
        return generate(request, ResolvedModelExecution.none());
    }

    /**
     * 把解码/校验失败转换为对外异常：MODEL 快照转为
     * {@code SelectedModelFailureException.invalidResponse}（模型输出无效，整轮终止）；
     * 其余转为 {@link GeneralKnowledgeUnavailableException}，原因链保留在内部不外泄。
     */
    private RuntimeException invalidResponse(
            ResolvedModelExecution modelExecution,
            RuntimeException cause) {
        if (modelExecution.getSnapshot().getKind()
                == ModelExecutionSnapshot.Kind.MODEL) {
            return SelectedModelFailureException.invalidResponse(cause);
        }
        return new GeneralKnowledgeUnavailableException(
                "general generation failed", cause);
    }
}
