package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.infrastructure.model.configuration.ConfiguredModelCatalog;
import com.portfolio.agent.infrastructure.model.configuration.ModelRuntimeProperties;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** 结构化模型测试夹具；不进入生产 JAR。 */
public final class StructuredModelTestFixtures {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final StructuredOutputContractRegistry CONTRACTS =
            StructuredOutputContractRegistry.standard();

    private StructuredModelTestFixtures() { }

    public static StructuredOutputContractRegistry contracts() {
        return CONTRACTS;
    }

    public static Map<ModelOperation, OperationBinding> nativeBindings() {
        return bindings(StructuredOutputStrategy.NATIVE_JSON_SCHEMA, TokenFieldPolicy.MAX_TOKENS);
    }

    public static Map<ModelOperation, OperationBinding> toolBindings() {
        return bindings(StructuredOutputStrategy.REQUIRED_TOOL_CALL, TokenFieldPolicy.MAX_TOKENS);
    }

    public static Map<ModelOperation, OperationBinding> v4NativeBindings() {
        EnumMap<ModelOperation, OperationBinding> result =
                new EnumMap<>(ModelOperation.class);
        result.put(ModelOperation.TURN_INTERPRETATION, dualBinding(
                ModelOperation.TURN_INTERPRETATION,
                "goal.provider-draft.v2", "goal.proposal.v5",
                OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                StructuredOutputStrategy.NATIVE_JSON_SCHEMA,
                TokenFieldPolicy.MAX_TOKENS));
        result.put(ModelOperation.GENERAL_KNOWLEDGE, binding(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2",
                StructuredOutputStrategy.NATIVE_JSON_SCHEMA,
                TokenFieldPolicy.MAX_TOKENS));
        return Map.copyOf(result);
    }

    public static Map<ModelOperation, OperationBinding> qwenV6ToolBindings() {
        EnumMap<ModelOperation, OperationBinding> result =
                new EnumMap<>(ModelOperation.class);
        result.put(ModelOperation.TURN_INTERPRETATION, dualBinding(
                ModelOperation.TURN_INTERPRETATION,
                "goal.provider-draft.v2", "goal.proposal.v5",
                OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                StructuredOutputStrategy.REQUIRED_TOOL_CALL,
                TokenFieldPolicy.OMIT));
        result.put(ModelOperation.GENERAL_KNOWLEDGE, dualBinding(
                ModelOperation.GENERAL_KNOWLEDGE,
                "general.provider-draft.v3", "general.draft.v2",
                OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION,
                StructuredOutputStrategy.REQUIRED_TOOL_CALL,
                TokenFieldPolicy.OMIT));
        return Map.copyOf(result);
    }

    public static Map<ModelOperation, OperationBinding> bindings(
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy) {
        EnumMap<ModelOperation, OperationBinding> result =
                new EnumMap<>(ModelOperation.class);
        result.put(ModelOperation.TURN_INTERPRETATION, binding(
                ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5",
                strategy, tokenFieldPolicy));
        result.put(ModelOperation.GENERAL_KNOWLEDGE, binding(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2",
                strategy, tokenFieldPolicy));
        return Map.copyOf(result);
    }

    public static StructuredOutputGateway gateway(StructuredModelTransport transport) {
        return new StructuredOutputGateway(transport, CONTRACTS);
    }

    public static ModelOperationPolicyRegistry operationPolicies() {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.TURN_INTERPRETATION,
                new ModelOperationPolicy(
                        ModelOperation.TURN_INTERPRETATION,
                        OperationMode.ENABLED, "goal.proposal.v5",
                        1600, Duration.ofSeconds(10)),
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        OperationMode.ENABLED, "general.draft.v2",
                        1600, Duration.ofSeconds(10))));
    }

    public static ConfiguredModelCatalog catalog(ModelRuntimeProperties properties) {
        return new ConfiguredModelCatalog(properties, operationPolicies(), CONTRACTS);
    }

    public static ResolvedModelExecution resolvedModel(
            Map<ModelOperation, OperationBinding> operationBindings) {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelRef.of("test-model"), "test-model-v1", "Test model", 10,
                URI.create("https://provider.example/v1/chat/completions"),
                "test-model", ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                operationBindings, 32_000, 2_000);
        ModelTransportBinding binding = new ModelTransportBinding(
                descriptor.getModelRef(), descriptor.getDescriptorFingerprint(),
                descriptor.getEndpoint(), descriptor.getModelName(),
                descriptor.getProtocolProfile(), "test-credential",
                descriptor.getMaxOutputTokens(), operationBindings);
        return ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor), binding);
    }

    public static StructurallyValidatedOutput validatedGeneral(String raw) {
        return CONTRACTS.validate(new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"), raw);
    }

    public static StructurallyValidatedOutput uncheckedGeneral(String raw) {
        try {
            StructuredContractRef ref = new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2");
            JsonNode tree = MAPPER.readTree(raw);
            return new StructurallyValidatedOutput(
                    ref, CONTRACTS.resolve(ref).contractFingerprint(), tree);
        } catch (Exception failure) {
            throw new IllegalArgumentException("invalid test fixture", failure);
        }
    }

    private static OperationBinding binding(
            ModelOperation operation,
            String version,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy) {
        StructuredOutputContract contract = CONTRACTS.resolve(
                new StructuredContractRef(operation, version));
        return new OperationBinding(
                operation, contract.ref(), contract.contractFingerprint(),
                contract.ref(), contract.contractFingerprint(),
                contract.outputName(),
                OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION,
                strategy, tokenFieldPolicy,
                OperationBinding.REQUEST_COMPILER_VERSION,
                strategy == StructuredOutputStrategy.NATIVE_JSON_SCHEMA
                        ? OperationBinding.NATIVE_RESPONSE_EXTRACTOR_VERSION
                        : OperationBinding.REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION);
    }

    private static OperationBinding dualBinding(
            ModelOperation operation,
            String providerVersion,
            String applicationVersion,
            String compilerVersion,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy) {
        StructuredOutputContract provider = CONTRACTS.resolve(
                new StructuredContractRef(operation, providerVersion));
        StructuredOutputContract application = CONTRACTS.resolve(
                new StructuredContractRef(operation, applicationVersion));
        return new OperationBinding(
                operation, provider.ref(), provider.contractFingerprint(),
                application.ref(), application.contractFingerprint(),
                provider.outputName(), compilerVersion,
                strategy, tokenFieldPolicy,
                OperationBinding.REQUEST_COMPILER_VERSION,
                strategy == StructuredOutputStrategy.NATIVE_JSON_SCHEMA
                        ? OperationBinding.NATIVE_RESPONSE_EXTRACTOR_VERSION
                        : OperationBinding.REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION);
    }
}
