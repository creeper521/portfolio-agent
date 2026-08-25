package com.portfolio.agent.turn.infrastructure;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 运行时就绪度：启动期一次判定的共享只读权威，供模型端口与公众就绪检查使用。
 *
 * <p>构造时做 fail-closed 校验——任何 ENABLED 模型操作的 schema 版本必须与生产
 * codec 一致，否则启动失败；Agent 可用性由会话上下文模式（非 DISABLED）决定，
 * 各操作的可用性 = Agent 可用 且 该操作策略为 ENABLED。构造后不可变。</p>
 */
public final class AgentRuntimeReadiness {
    private final boolean agentAvailable;
    private final Map<ModelOperation, Boolean> operationAvailability;

    public AgentRuntimeReadiness(
            ConversationContextProperties.Mode contextMode,
            ModelOperationPolicyRegistry policies) {
        Objects.requireNonNull(contextMode, "contextMode");
        Objects.requireNonNull(policies, "policies");
        agentAvailable = contextMode != ConversationContextProperties.Mode.DISABLED;
        EnumMap<ModelOperation, Boolean> availability =
                new EnumMap<>(ModelOperation.class);
        for (ModelOperation operation : ModelOperation.values()) {
            ModelOperationPolicy policy = policies.get(operation);
            validateEnabledAuthority(policy);
            availability.put(operation,
                    agentAvailable
                            && policy.getMode() == OperationMode.ENABLED);
        }
        operationAvailability = Map.copyOf(availability);
    }

    public boolean isAgentAvailable() {
        return agentAvailable;
    }

    public boolean isOperationAvailable(ModelOperation operation) {
        return operationAvailability.get(Objects.requireNonNull(operation, "operation"));
    }

    /** fail-closed 门禁：ENABLED 操作的 schema 版本与生产 codec 不一致即启动失败。 */
    private void validateEnabledAuthority(ModelOperationPolicy policy) {
        if (policy.getMode() != OperationMode.ENABLED) return;
        if (!expectedSchema(policy.getOperation()).equals(policy.getSchemaVersion())) {
            throw new IllegalStateException(
                    "enabled model operation schema does not match production codec: "
                            + policy.getOperation());
        }
    }

    private String expectedSchema(ModelOperation operation) {
        return switch (operation) {
            case TURN_INTERPRETATION -> GoalProposalCodec.SCHEMA_VERSION;
            case GENERAL_KNOWLEDGE -> GeneralDraftCodec.SCHEMA_VERSION;
        };
    }
}
