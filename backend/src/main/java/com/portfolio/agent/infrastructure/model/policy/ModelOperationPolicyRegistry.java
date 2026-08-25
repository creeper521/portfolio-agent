package com.portfolio.agent.infrastructure.model.policy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Operation 策略注册表：冻结的、彼此独立授权的模型 Operation 集合。
 *
 * <p>构造期把传入策略补全为覆盖全部 {@link ModelOperation} 的封闭视图——
 * 缺席的 Operation 一律补 DISABLED 策略（fail-closed，不继承其他 Operation
 * 的批准），随后对每个策略执行启动校验，任何 ENABLED 但配置不完整的
 * Operation 都会让构造失败。
 */
public final class ModelOperationPolicyRegistry {
    private final Map<ModelOperation, ModelOperationPolicy> policies;

    /**
     * 构造注册表：拷贝入参、补全缺席的 DISABLED 策略并做启动校验。
     *
     * @param values 已配置的策略，键为 Operation，允许不完整
     * @throws IllegalStateException 任一 ENABLED 策略配置不完整时抛出
     */
    public ModelOperationPolicyRegistry(Map<ModelOperation, ModelOperationPolicy> values) {
        Objects.requireNonNull(values, "values");
        EnumMap<ModelOperation, ModelOperationPolicy> copy =
                new EnumMap<>(ModelOperation.class);
        copy.putAll(values);
        for (ModelOperation operation : ModelOperation.values()) {
            copy.putIfAbsent(operation, disabled(operation));
        }
        copy.values().forEach(ModelOperationPolicy::validateStartup);
        policies = Map.copyOf(copy);
    }

    /** 全 DISABLED 的缺省注册表：任何 Operation 都未获批准的安全空集。 */
    public static ModelOperationPolicyRegistry defaults() {
        EnumMap<ModelOperation, ModelOperationPolicy> values =
                new EnumMap<>(ModelOperation.class);
        for (ModelOperation operation : ModelOperation.values()) {
            values.put(operation, disabled(operation));
        }
        return new ModelOperationPolicyRegistry(values);
    }

    /** 取指定 Operation 的策略；注册表构造后必然覆盖全部 Operation。 */
    public ModelOperationPolicy get(ModelOperation operation) {
        return policies.get(Objects.requireNonNull(operation, "operation"));
    }

    /** 以不可变 Map 视图返回全部策略。 */
    public Map<ModelOperation, ModelOperationPolicy> asMap() {
        return policies;
    }

    /** 构造指定 Operation 的 DISABLED 占位策略，用于补全缺席项。 */
    private static ModelOperationPolicy disabled(ModelOperation operation) {
        return new ModelOperationPolicy(
                operation, OperationMode.DISABLED, null, 0, null);
    }
}
