package com.portfolio.agent.infrastructure.model;

/**
 * 已解析的模型执行：把免凭证快照与服务端专用传输绑定配对后的服务端载体。
 *
 * <p>构造期保证两者强一致：MODEL 快照必须配绑定、NONE 快照必须无绑定，
 * 且快照与绑定必须指向同一个 modelRef——任何不一致都直接抛出
 * IllegalArgumentException，防止"快照说 A、实际调用 B"的错位。
 *
 * <p>实例同时记录每个模型阶段（{@link Stage}）的"已尝试 / 已采纳"标记，
 * 供 Turn 结算阶段区分"调用失败"与"调用成功但结果被丢弃"等终态。
 */
public final class ResolvedModelExecution {
    private final ModelExecutionSnapshot snapshot;
    private final ModelTransportBinding binding;
    private final java.util.concurrent.atomic.AtomicBoolean goalAttempted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean goalAdopted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean answerAttempted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean answerAdopted =
            new java.util.concurrent.atomic.AtomicBoolean();

    private ResolvedModelExecution(
            ModelExecutionSnapshot snapshot,
            ModelTransportBinding binding) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
        this.binding = binding;
        if ((snapshot.getKind() == ModelExecutionSnapshot.Kind.MODEL)
                != (binding != null)) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding must agree");
        }
        if (binding != null && !snapshot.getModelRef().orElseThrow()
                .equals(binding.getModelRef())) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding must identify the same model");
        }
        if (binding != null && !snapshot.getDescriptorFingerprint().orElseThrow()
                .equals(binding.getDescriptorFingerprint())) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding descriptor must agree");
        }
        if (binding != null && !bindingFingerprints(snapshot.getOperationBindings())
                .equals(bindingFingerprints(binding.getOperationBindings()))) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding operations must agree");
        }
    }

    /** 构造显式不使用模型的执行：NONE 快照且无绑定。 */
    public static ResolvedModelExecution none() {
        return new ResolvedModelExecution(ModelExecutionSnapshot.none(), null);
    }

    /** 用 MODEL 快照与其传输绑定构造执行；两者必须指向同一 modelRef。 */
    public static ResolvedModelExecution model(
            ModelExecutionSnapshot snapshot,
            ModelTransportBinding binding) {
        return new ResolvedModelExecution(snapshot, binding);
    }

    public ModelExecutionSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * 取传输绑定；仅在 MODEL 执行中存在。
     *
     * @throws IllegalStateException 当前执行为 NONE（显式不使用模型）时
     */
    public ModelTransportBinding getRequiredBinding() {
        if (binding == null) {
            throw new IllegalStateException("NONE model execution has no transport binding");
        }
        return binding;
    }

    /** 标记指定阶段已发起模型调用（尚未或未必采纳结果）。 */
    public void markAttempted(Stage stage) {
        attempted(stage).set(true);
    }

    /** 标记指定阶段的模型输出已被采纳（隐含已尝试）。 */
    public void markAdopted(Stage stage) {
        attempted(stage).set(true);
        adopted(stage).set(true);
    }

    /** 查询指定阶段是否发起过模型调用。 */
    public boolean wasAttempted(Stage stage) {
        return attempted(stage).get();
    }

    /** 查询指定阶段的模型输出是否被采纳。 */
    public boolean wasAdopted(Stage stage) {
        return adopted(stage).get();
    }

    private java.util.concurrent.atomic.AtomicBoolean attempted(Stage stage) {
        return switch (java.util.Objects.requireNonNull(stage, "stage")) {
            case GOAL_INTERPRETATION -> goalAttempted;
            case ANSWER_GENERATION -> answerAttempted;
        };
    }

    private java.util.concurrent.atomic.AtomicBoolean adopted(Stage stage) {
        return switch (java.util.Objects.requireNonNull(stage, "stage")) {
            case GOAL_INTERPRETATION -> goalAdopted;
            case ANSWER_GENERATION -> answerAdopted;
        };
    }

    @Override
    public String toString() {
        return "ResolvedModelExecution{" + snapshot + '}';
    }

    /** Turn 内的模型阶段：目标解释与答案生成各自独立追踪尝试/采纳状态。 */
    public enum Stage {
        GOAL_INTERPRETATION,
        ANSWER_GENERATION
    }

    private static java.util.Map<
            com.portfolio.agent.infrastructure.model.policy.ModelOperation, String>
            bindingFingerprints(java.util.Map<
                    com.portfolio.agent.infrastructure.model.policy.ModelOperation,
                    com.portfolio.agent.infrastructure.model.structured.OperationBinding> values) {
        java.util.EnumMap<
                com.portfolio.agent.infrastructure.model.policy.ModelOperation, String> result =
                new java.util.EnumMap<>(
                        com.portfolio.agent.infrastructure.model.policy.ModelOperation.class);
        values.forEach((operation, operationBinding) -> result.put(
                operation, operationBinding.getBindingFingerprint()));
        return java.util.Map.copyOf(result);
    }
}
