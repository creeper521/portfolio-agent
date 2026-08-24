package com.portfolio.agent.infrastructure.model.policy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Frozen set of independently authorized model operations. */
public final class ModelOperationPolicyRegistry {
    private final Map<ModelOperation, ModelOperationPolicy> policies;

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

    public static ModelOperationPolicyRegistry defaults() {
        EnumMap<ModelOperation, ModelOperationPolicy> values =
                new EnumMap<>(ModelOperation.class);
        for (ModelOperation operation : ModelOperation.values()) {
            values.put(operation, disabled(operation));
        }
        return new ModelOperationPolicyRegistry(values);
    }

    public ModelOperationPolicy get(ModelOperation operation) {
        return policies.get(Objects.requireNonNull(operation, "operation"));
    }

    public Map<ModelOperation, ModelOperationPolicy> asMap() {
        return policies;
    }

    private static ModelOperationPolicy disabled(ModelOperation operation) {
        return new ModelOperationPolicy(
                operation, OperationMode.DISABLED, null, 0, null);
    }
}
