package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;

import java.util.Objects;

/** 单模型、单 Operation 在一个 Turn 内冻结的结构化协议绑定。 */
public final class OperationBinding {
    public static final String REQUEST_COMPILER_VERSION = "openai-structured-request-v2";
    public static final String NATIVE_RESPONSE_EXTRACTOR_VERSION =
            "openai-native-json-schema-response-v1";
    public static final String REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION =
            "openai-required-tool-calls-response-v1";
    public static final String REQUIRED_TOOL_STOP_RESPONSE_EXTRACTOR_VERSION =
            "qwen-required-tool-stop-response-v1";
    public static final String IDENTITY_OUTPUT_COMPILER_VERSION =
            "identity-output-compiler-v1";
    public static final String GOAL_DRAFT_OUTPUT_COMPILER_VERSION =
            "goal-provider-draft-to-v5-v1";
    public static final String GENERAL_DRAFT_OUTPUT_COMPILER_VERSION =
            "general-provider-draft-compiler.v2";

    private final ModelOperation operation;
    private final StructuredContractRef providerContractRef;
    private final String providerContractFingerprint;
    private final StructuredContractRef applicationContractRef;
    private final String applicationContractFingerprint;
    private final String outputName;
    private final String outputCompilerProfileVersion;
    private final StructuredOutputStrategy strategy;
    private final TokenFieldPolicy tokenFieldPolicy;
    private final String requestCompilerProfileVersion;
    private final String responseExtractorProfileVersion;
    private final String bindingFingerprint;

    public OperationBinding(
            ModelOperation operation,
            StructuredContractRef providerContractRef,
            String providerContractFingerprint,
            StructuredContractRef applicationContractRef,
            String applicationContractFingerprint,
            String outputName,
            String outputCompilerProfileVersion,
            StructuredOutputStrategy strategy,
            TokenFieldPolicy tokenFieldPolicy,
            String requestCompilerProfileVersion,
            String responseExtractorProfileVersion) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.providerContractRef = requireOperation(
                providerContractRef, operation, "providerContractRef");
        this.providerContractFingerprint = fingerprint(
                providerContractFingerprint, "providerContractFingerprint");
        this.applicationContractRef = requireOperation(
                applicationContractRef, operation, "applicationContractRef");
        this.applicationContractFingerprint = fingerprint(
                applicationContractFingerprint, "applicationContractFingerprint");
        this.outputName = text(outputName, "outputName");
        this.outputCompilerProfileVersion = text(
                outputCompilerProfileVersion, "outputCompilerProfileVersion");
        requireApprovedCompiler();
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.tokenFieldPolicy = Objects.requireNonNull(tokenFieldPolicy, "tokenFieldPolicy");
        this.requestCompilerProfileVersion = text(
                requestCompilerProfileVersion, "requestCompilerProfileVersion");
        this.responseExtractorProfileVersion = text(
                responseExtractorProfileVersion, "responseExtractorProfileVersion");
        requireApprovedExtractor();
        bindingFingerprint = ModelProviderDescriptor.fingerprint(
                operation.name(), this.providerContractRef.schemaVersion(),
                this.providerContractFingerprint,
                this.applicationContractRef.schemaVersion(),
                this.applicationContractFingerprint,
                this.outputName, this.outputCompilerProfileVersion, strategy.name(),
                tokenFieldPolicy.name(), this.requestCompilerProfileVersion,
                this.responseExtractorProfileVersion);
    }

    public ModelOperation getOperation() { return operation; }
    public StructuredContractRef getContractRef() { return applicationContractRef; }
    public StructuredContractRef getProviderContractRef() { return providerContractRef; }
    public String getProviderContractFingerprint() {
        return providerContractFingerprint;
    }
    public StructuredContractRef getApplicationContractRef() {
        return applicationContractRef;
    }
    public String getApplicationContractFingerprint() {
        return applicationContractFingerprint;
    }
    public String getOutputCompilerProfileVersion() {
        return outputCompilerProfileVersion;
    }
    public StructuredOutputStrategy getStrategy() { return strategy; }
    public TokenFieldPolicy getTokenFieldPolicy() { return tokenFieldPolicy; }
    public String getRequestCompilerProfileVersion() { return requestCompilerProfileVersion; }
    public String getResponseExtractorProfileVersion() { return responseExtractorProfileVersion; }
    public String getBindingFingerprint() { return bindingFingerprint; }

    public boolean acceptsFinishReason(String finishReason) {
        return switch (responseExtractorProfileVersion) {
            case NATIVE_RESPONSE_EXTRACTOR_VERSION,
                    REQUIRED_TOOL_STOP_RESPONSE_EXTRACTOR_VERSION ->
                    "stop".equals(finishReason);
            case REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION ->
                    "tool_calls".equals(finishReason);
            default -> false;
        };
    }

    public String outputToolName() {
        return "emit_" + outputName;
    }

    private static String fingerprint(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static StructuredContractRef requireOperation(
            StructuredContractRef ref, ModelOperation operation, String name) {
        StructuredContractRef value = Objects.requireNonNull(ref, name);
        if (value.operation() != operation) {
            throw new IllegalArgumentException(
                    "operation and " + name + " must agree");
        }
        return value;
    }

    private void requireApprovedExtractor() {
        boolean approved = switch (strategy) {
            case NATIVE_JSON_SCHEMA -> NATIVE_RESPONSE_EXTRACTOR_VERSION.equals(
                    responseExtractorProfileVersion);
            case REQUIRED_TOOL_CALL ->
                    REQUIRED_TOOL_CALLS_RESPONSE_EXTRACTOR_VERSION.equals(
                            responseExtractorProfileVersion)
                    || REQUIRED_TOOL_STOP_RESPONSE_EXTRACTOR_VERSION.equals(
                            responseExtractorProfileVersion);
        };
        if (!approved) {
            throw new IllegalArgumentException(
                    "response extractor is incompatible with strategy");
        }
    }

    private void requireApprovedCompiler() {
        boolean sameContract = providerContractRef.equals(applicationContractRef)
                && providerContractFingerprint.equals(
                        applicationContractFingerprint);
        boolean identity = IDENTITY_OUTPUT_COMPILER_VERSION.equals(
                outputCompilerProfileVersion);
        boolean goalDraft = GOAL_DRAFT_OUTPUT_COMPILER_VERSION.equals(
                outputCompilerProfileVersion);
        boolean generalDraft = GENERAL_DRAFT_OUTPUT_COMPILER_VERSION.equals(
                outputCompilerProfileVersion);
        boolean approvedDualCompiler = operation == ModelOperation.TURN_INTERPRETATION
                ? goalDraft
                : operation == ModelOperation.GENERAL_KNOWLEDGE && generalDraft;
        if ((!identity && !goalDraft && !generalDraft)
                || (sameContract && !identity)
                || (!sameContract && !approvedDualCompiler)) {
            throw new IllegalArgumentException(
                    "output compiler is incompatible with operation contracts");
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 96) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}
