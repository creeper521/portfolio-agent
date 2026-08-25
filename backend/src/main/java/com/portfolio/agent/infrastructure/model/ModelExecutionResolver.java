package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.Objects;

/**
 * 模型执行解析器：把 Turn 显式选择的 {@code MODEL(modelRef + selectionVersion)}
 * 解析为可执行的快照与传输绑定。
 *
 * <p>本类是三重 fail-closed 准入中"解析"一环的守门者：它只面对启动时冻结的
 * 目录快照（{@link ModelCatalogSnapshot}）与传输绑定源，任何一项不满足都抛出
 * {@link ModelExecutionResolutionException}，绝不隐式回退到其他 Provider 或模型。
 *
 * <p>关键不变量：
 * <ul>
 *   <li>选择为 NONE 时直接返回 {@link ResolvedModelExecution#none()}，不接触目录；</li>
 *   <li>modelRef 在目录中不存在、或对应的传输绑定缺失时，视为所选模型不可用；</li>
 *   <li>请求携带的 selectionVersion 与目录中该模型当前版本不一致时判定为过期选择，
 *       拒绝执行（调用方必须携带新版本重试，而不是静默用旧模型）。</li>
 * </ul>
 */
public final class ModelExecutionResolver {
    private final ModelCatalogSnapshot catalog;
    private final BindingSource bindings;

    /**
     * 构造解析器。
     *
     * @param catalog 启动时冻结的模型目录快照，不接受 null
     * @param bindings 传输绑定源，负责按 modelRef 提供服务端专用绑定
     */
    public ModelExecutionResolver(
            ModelCatalogSnapshot catalog,
            BindingSource bindings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    /**
     * 解析 Turn 的显式模型选择。
     *
     * <p>NONE 选择直接返回免模型执行；MODEL 选择依次校验 modelRef 存在于冻结目录、
     * 传输绑定可用、selectionVersion 与目录一致，全部通过后组装
     * {@link ResolvedModelExecution}。任何一步失败都不会尝试替代模型。
     *
     * @param selection Turn 携带的显式模型选择，不允许为 null
     * @return 与选择匹配的已解析执行（NONE 或 MODEL）
     * @throws ModelExecutionResolutionException 选择为 MODEL 但模型在目录/绑定中缺失
     *         （SELECTED_MODEL_UNAVAILABLE），或 selectionVersion 已过期
     *         （MODEL_SELECTION_STALE）
     */
    public ResolvedModelExecution resolve(
            AgentTurnCommand.ModelSelection selection) {
        AgentTurnCommand.ModelSelection required = Objects.requireNonNull(
                selection, "selection");
        if (required.getKind() == AgentTurnCommand.ModelSelectionKind.NONE) {
            return ResolvedModelExecution.none();
        }

        ModelRef modelRef = ModelRef.of(required.getModelRef().orElseThrow());
        ModelProviderDescriptor descriptor;
        try {
            descriptor = catalog.getRequiredDescriptor(modelRef);
        } catch (IllegalArgumentException unavailable) {
            // 目录查不到该 modelRef：视为所选模型不可用，而不是降级到其他模型。
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
        }
        ModelTransportBinding binding;
        try {
            binding = bindings.getRequiredBinding(modelRef);
        } catch (IllegalArgumentException unavailable) {
            // 有描述符但无传输绑定（如该 Provider 未通过准入）：同样按不可用拒绝。
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
        }
        if (!descriptor.getSelectionVersion().equals(
                required.getSelectionVersion().orElseThrow())) {
            // 版本不一致说明客户端基于旧目录发起请求，必须显式刷新选择。
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.MODEL_SELECTION_STALE);
        }
        return ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor), binding);
    }

    /**
     * 传输绑定源：按 modelRef 提供服务端专用 {@link ModelTransportBinding}。
     * 由基础设施装配实现，缺失时抛出 IllegalArgumentException。
     */
    @FunctionalInterface
    public interface BindingSource {
        ModelTransportBinding getRequiredBinding(ModelRef modelRef);
    }
}
