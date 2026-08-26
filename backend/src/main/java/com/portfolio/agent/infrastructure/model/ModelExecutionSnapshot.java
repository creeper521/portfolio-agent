package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.util.Optional;
import java.util.Set;
import java.util.Map;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;

/**
 * 单个 Turn 内所有模型阶段共享的、请求时的模型执行快照。
 *
 * <p>快照在 Claim 之后由 {@link ModelExecutionResolver} 冻结，内容取自
 * {@link ModelProviderDescriptor} 的免凭证字段（协议画像、能力集、上下文/输出
 * token 上限等）。它不含任何凭证或 endpoint，因此可以安全地作为公开投影的输入，
 * 也不会因为后续目录刷新而在 Turn 生命周期内漂移。
 *
 * <p>两种形态：{@link Kind#MODEL} 携带完整描述符投影；{@link Kind#NONE} 是
 * 共享的单例空快照，表示该 Turn 显式选择不使用任何模型。
 */
public final class ModelExecutionSnapshot {
    private static final ModelExecutionSnapshot NONE = new ModelExecutionSnapshot();

    private final Kind kind;
    private final ModelRef modelRef;
    private final String selectionVersion;
    private final String descriptorFingerprint;
    private final ModelProviderProtocolProfile protocolProfile;
    private final Set<ModelCapability> capabilities;
    private final int maxContextTokens;
    private final int maxOutputTokens;
    private final Map<ModelOperation, OperationBinding> operationBindings;

    private ModelExecutionSnapshot() {
        kind = Kind.NONE;
        modelRef = null;
        selectionVersion = null;
        descriptorFingerprint = null;
        protocolProfile = null;
        capabilities = Set.of();
        maxContextTokens = 0;
        maxOutputTokens = 0;
        operationBindings = Map.of();
    }

    private ModelExecutionSnapshot(ModelProviderDescriptor descriptor) {
        ModelProviderDescriptor required = java.util.Objects.requireNonNull(
                descriptor, "descriptor");
        kind = Kind.MODEL;
        modelRef = required.getModelRef();
        selectionVersion = required.getSelectionVersion();
        descriptorFingerprint = required.getDescriptorFingerprint();
        protocolProfile = required.getProtocolProfile();
        capabilities = Set.copyOf(required.getCapabilities());
        maxContextTokens = required.getMaxContextTokens();
        maxOutputTokens = required.getMaxOutputTokens();
        operationBindings = Map.copyOf(required.getOperationBindings());
    }

    /** 返回共享的 NONE 单例快照。 */
    public static ModelExecutionSnapshot none() {
        return NONE;
    }

    /**
     * 从目录描述符冻结一份 MODEL 形态快照。
     *
     * <p>内部对能力集做防御性拷贝，确保快照与目录后续变更隔离。
     *
     * @param descriptor 已通过准入的 Provider 模型描述符，不允许为 null
     */
    public static ModelExecutionSnapshot model(ModelProviderDescriptor descriptor) {
        return new ModelExecutionSnapshot(descriptor);
    }

    public Kind getKind() {
        return kind;
    }

    public Optional<ModelRef> getModelRef() {
        return Optional.ofNullable(modelRef);
    }

    public Optional<String> getSelectionVersion() {
        return Optional.ofNullable(selectionVersion);
    }

    public Optional<String> getDescriptorFingerprint() {
        return Optional.ofNullable(descriptorFingerprint);
    }

    public Optional<ModelProviderProtocolProfile> getProtocolProfile() {
        return Optional.ofNullable(protocolProfile);
    }

    public Set<ModelCapability> getCapabilities() {
        return capabilities;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Map<ModelOperation, OperationBinding> getOperationBindings() {
        return operationBindings;
    }

    public OperationBinding getRequiredOperationBinding(ModelOperation operation) {
        OperationBinding binding = operationBindings.get(
                java.util.Objects.requireNonNull(operation, "operation"));
        if (binding == null) {
            throw new IllegalArgumentException("model operation binding is not available");
        }
        return binding;
    }

    /** 判断快照是否声明了指定能力；NONE 快照恒为 false。 */
    public boolean supports(ModelCapability capability) {
        return capabilities.contains(java.util.Objects.requireNonNull(
                capability, "capability"));
    }

    @Override
    public String toString() {
        return kind == Kind.NONE
                ? "ModelExecutionSnapshot{kind=NONE}"
                : "ModelExecutionSnapshot{kind=MODEL, modelRef=" + modelRef
                + ", selectionVersion=" + selectionVersion
                + ", descriptorFingerprint=" + descriptorFingerprint + '}';
    }

    /** 快照形态：MODEL 为真实模型投影，NONE 表示显式不使用模型。 */
    public enum Kind {
        MODEL,
        NONE
    }
}
