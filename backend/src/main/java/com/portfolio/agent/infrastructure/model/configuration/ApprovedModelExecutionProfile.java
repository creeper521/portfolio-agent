package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContract;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputStrategy;
import com.portfolio.agent.infrastructure.model.structured.TokenFieldPolicy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 代码所有的模型执行画像。环境只选择 profileId，不能拼装 strategy、carrier、
 * tool name、token 字段或 extractor。
 */
public final class ApprovedModelExecutionProfile {
    public static final String QWEN_PROFILE = "QWEN_3_7_FLASH_STRUCTURED_V7";
    public static final String GLM_PROFILE = "GLM_4_7_FLASH_STRUCTURED_V4";

    private final String profileId;
    private final String requiredSelectionVersion;
    private final String expectedModelIdentity;
    private final ModelProviderProtocolProfile protocolProfile;
    private final Map<ModelOperation, OperationBinding> operationBindings;

    private ApprovedModelExecutionProfile(
            String profileId,
            String requiredSelectionVersion,
            String expectedModelIdentity,
            ModelProviderProtocolProfile protocolProfile,
            Map<ModelOperation, OperationBinding> operationBindings) {
        this.profileId = profileId;
        this.requiredSelectionVersion = requiredSelectionVersion;
        this.expectedModelIdentity = expectedModelIdentity;
        this.protocolProfile = protocolProfile;
        this.operationBindings = Map.copyOf(operationBindings);
    }

    public static ApprovedModelExecutionProfile resolve(
            String profileId,
            StructuredOutputContractRegistry contracts) {
        Objects.requireNonNull(contracts, "contracts");
        ApprovedModelExecutionProfile profile = switch (profileId) {
            case QWEN_PROFILE -> qwenProfile(contracts,
                    QWEN_PROFILE, "qwen-3-7-flash-v7", "qwen3.7-flash",
                    ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS,
                    StructuredOutputStrategy.REQUIRED_TOOL_CALL,
                    TokenFieldPolicy.OMIT,
                    OperationBinding.REQUIRED_TOOL_STOP_RESPONSE_EXTRACTOR_VERSION);
            case GLM_PROFILE -> glmProfile(contracts,
                    GLM_PROFILE, "glm-4-7-flash-v4", "glm-4.7-flash",
                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                    StructuredOutputStrategy.REQUIRED_TOOL_CALL,
                    TokenFieldPolicy.MAX_TOKENS,
                    OperationBinding.REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION);
            default -> throw new IllegalArgumentException(
                    "model execution profile is not approved");
        };
        for (OperationBinding binding : profile.operationBindings.values()) {
            contracts.resolve(binding.getProviderContractRef());
            contracts.resolve(binding.getApplicationContractRef());
        }
        return profile;
    }

    private static ApprovedModelExecutionProfile qwenProfile(
            StructuredOutputContractRegistry contracts,
            String profileId,
            String selectionVersion,
            String modelIdentity,
            ModelProviderProtocolProfile protocolProfile,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy,
            String responseExtractorProfileVersion) {
        EnumMap<ModelOperation, OperationBinding> bindings =
                new EnumMap<>(ModelOperation.class);
        bindings.put(ModelOperation.TURN_INTERPRETATION, binding(
                contracts,
                ModelOperation.TURN_INTERPRETATION,
                "goal.provider-draft.v2", "goal.proposal.v5",
                OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                strategy, tokenFieldPolicy, responseExtractorProfileVersion));
        bindings.put(ModelOperation.GENERAL_KNOWLEDGE, binding(
                contracts,
                ModelOperation.GENERAL_KNOWLEDGE,
                "general.provider-draft.v4", "general.draft.v3",
                OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION,
                strategy, tokenFieldPolicy, responseExtractorProfileVersion));
        return new ApprovedModelExecutionProfile(
                profileId, selectionVersion, modelIdentity,
                protocolProfile, bindings);
    }

    private static ApprovedModelExecutionProfile glmProfile(
            StructuredOutputContractRegistry contracts,
            String profileId,
            String selectionVersion,
            String modelIdentity,
            ModelProviderProtocolProfile protocolProfile,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy,
            String responseExtractorProfileVersion) {
        EnumMap<ModelOperation, OperationBinding> bindings =
                new EnumMap<>(ModelOperation.class);
        bindings.put(ModelOperation.TURN_INTERPRETATION, binding(
                contracts,
                ModelOperation.TURN_INTERPRETATION,
                "goal.provider-draft.v2", "goal.proposal.v5",
                OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                strategy, tokenFieldPolicy, responseExtractorProfileVersion));
        bindings.put(ModelOperation.GENERAL_KNOWLEDGE, binding(
                contracts,
                ModelOperation.GENERAL_KNOWLEDGE,
                "general.draft.v2", "general.draft.v2",
                OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION,
                strategy, tokenFieldPolicy, responseExtractorProfileVersion));
        return new ApprovedModelExecutionProfile(
                profileId, selectionVersion, modelIdentity,
                protocolProfile, bindings);
    }

    private static OperationBinding binding(
            StructuredOutputContractRegistry contracts,
            ModelOperation operation,
            String providerSchemaVersion,
            String applicationSchemaVersion,
            String outputCompilerProfileVersion,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy,
            String responseExtractorProfileVersion) {
        StructuredOutputContract providerContract = contracts.resolve(
                new StructuredContractRef(operation, providerSchemaVersion));
        StructuredOutputContract applicationContract = contracts.resolve(
                new StructuredContractRef(operation, applicationSchemaVersion));
        return new OperationBinding(
                operation, providerContract.ref(),
                providerContract.contractFingerprint(),
                applicationContract.ref(),
                applicationContract.contractFingerprint(),
                providerContract.outputName(),
                outputCompilerProfileVersion,
                strategy, tokenFieldPolicy,
                OperationBinding.REQUEST_COMPILER_VERSION,
                responseExtractorProfileVersion);
    }

    public void requireMatches(
            String selectionVersion,
            String modelIdentity,
            ModelProviderProtocolProfile protocolProfile) {
        if (!requiredSelectionVersion.equals(selectionVersion)) {
            throw new IllegalArgumentException(
                    "selectionVersion does not match model execution profile");
        }
        if (!expectedModelIdentity.equals(modelIdentity)) {
            throw new IllegalArgumentException(
                    "model identity does not match model execution profile");
        }
        if (this.protocolProfile != protocolProfile) {
            throw new IllegalArgumentException(
                    "protocol profile does not match model execution profile");
        }
    }

    public String getProfileId() { return profileId; }
    public String getRequiredSelectionVersion() { return requiredSelectionVersion; }
    public String getExpectedModelIdentity() { return expectedModelIdentity; }
    public ModelProviderProtocolProfile getProtocolProfile() { return protocolProfile; }
    public Map<ModelOperation, OperationBinding> getOperationBindings() {
        return operationBindings;
    }
}
