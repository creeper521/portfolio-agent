package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;

import java.util.Objects;

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
        ModelTransportBinding binding = Objects.requireNonNull(
                modelBinding, "modelBinding");
        OperationBinding operationBinding = binding.getRequiredOperationBinding(
                Objects.requireNonNull(request, "request").operation());
        StructuredOutputCompiler requiredCompiler = Objects.requireNonNull(
                compiler, "compiler");
        if (!operationBinding.getOutputCompilerProfileVersion().equals(
                requiredCompiler.profileVersion())) {
            throw new IllegalArgumentException(
                    "output compiler profile does not match operation binding");
        }
        StructuredModelResponse response = transport.execute(binding, request);
        StructurallyValidatedOutput providerOutput;
        try {
            providerOutput = response.validateWith(
                    contracts, operationBinding.getProviderContractRef());
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
