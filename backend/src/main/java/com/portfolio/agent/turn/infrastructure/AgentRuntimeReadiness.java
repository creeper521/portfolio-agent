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

/** Startup-validated authority shared by model ports and public Agent readiness. */
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
