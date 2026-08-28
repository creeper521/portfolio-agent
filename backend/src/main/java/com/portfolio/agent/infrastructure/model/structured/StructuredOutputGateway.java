package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;

import java.util.Objects;
import java.util.UUID;

/** Domain Adapter 唯一生产入口：一次传输后对同一 binding 合同严格解析与本地校验。 */
public final class StructuredOutputGateway {
    private final StructuredModelTransport transport;
    private final StructuredOutputContractRegistry contracts;

    public StructuredOutputGateway(
            StructuredModelTransport transport,
            StructuredOutputContractRegistry contracts) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
    }

    public StructurallyValidatedOutput execute(
            ModelTransportBinding modelBinding,
            StructuredModelRequest request) {
        return execute(modelBinding, request, StructuredOutputCompiler.identity());
    }

    public StructurallyValidatedOutput execute(
            ModelTransportBinding modelBinding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler) {
        return execute(modelBinding, request, compiler,
                StructuredOutputSchemaFailureClassifier.generic());
    }

    public StructurallyValidatedOutput execute(
            ModelTransportBinding modelBinding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler,
            StructuredOutputSchemaFailureClassifier failureClassifier) {
        return execute(modelBinding, request, compiler, failureClassifier,
                ProviderAttemptContext.single(UUID.randomUUID()));
    }

    public StructurallyValidatedOutput execute(
            ModelTransportBinding modelBinding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler,
            ProviderAttemptContext attempt) {
        return execute(modelBinding, request, compiler,
                StructuredOutputSchemaFailureClassifier.generic(), attempt);
    }

    public StructurallyValidatedOutput execute(
            ModelTransportBinding modelBinding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler,
            StructuredOutputSchemaFailureClassifier failureClassifier,
            ProviderAttemptContext attempt) {
        ModelTransportBinding binding = Objects.requireNonNull(
                modelBinding, "modelBinding");
        OperationBinding operationBinding = binding.getRequiredOperationBinding(
                Objects.requireNonNull(request, "request").operation());
        StructuredOutputCompiler requiredCompiler = Objects.requireNonNull(
                compiler, "compiler");
        StructuredOutputSchemaFailureClassifier requiredFailureClassifier =
                Objects.requireNonNull(failureClassifier, "failureClassifier");
        ProviderAttemptContext requiredAttempt = Objects.requireNonNull(
                attempt, "attempt");
        if (!operationBinding.getOutputCompilerProfileVersion().equals(
                requiredCompiler.profileVersion())) {
            throw new IllegalArgumentException(
                    "output compiler profile does not match operation binding");
        }
        StructuredModelResponse response = transport.execute(
                binding, request, requiredAttempt);
        StructurallyValidatedOutput providerOutput;
        try {
            providerOutput = response.validateWith(
                    contracts, operationBinding.getProviderContractRef(),
                    requiredFailureClassifier);
        } catch (StructuredOutputValidationException failure) {
            throw failure.atStage(StructuredOutputValidationException.Stage
                    .PROVIDER_DRAFT_SCHEMA);
        }
        com.fasterxml.jackson.databind.JsonNode compiled;
        try {
            compiled = requiredCompiler.compile(providerOutput.jsonTree());
        } catch (StructuredOutputValidationException failure) {
            throw failure.atStage(StructuredOutputValidationException.Stage
                    .DETERMINISTIC_COMPILER);
        }
        try {
            return contracts.validateTree(
                    operationBinding.getApplicationContractRef(), compiled);
        } catch (StructuredOutputValidationException failure) {
            throw failure.atStage(StructuredOutputValidationException.Stage
                    .CANONICAL_SCHEMA);
        }
    }
}
