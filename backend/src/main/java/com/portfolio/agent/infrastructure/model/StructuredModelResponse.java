package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputSchemaFailureClassifier;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;

/**
 * Provider extractor 的封闭结果。原始 payload 不提供 getter，也不进入 toString；
 * 唯一消费方式是立刻交给 canonical contract registry 做严格解析和本地校验。
 */
public final class StructuredModelResponse {
    private final String extractedPayload;

    public StructuredModelResponse(String extractedPayload) {
        if (extractedPayload == null || extractedPayload.isBlank()) {
            throw new IllegalArgumentException("extracted payload is required");
        }
        this.extractedPayload = extractedPayload;
    }

    public StructurallyValidatedOutput validateWith(
            StructuredOutputContractRegistry contracts,
            StructuredContractRef contractRef) {
        return validateWith(contracts, contractRef,
                StructuredOutputSchemaFailureClassifier.generic());
    }

    public StructurallyValidatedOutput validateWith(
            StructuredOutputContractRegistry contracts,
            StructuredContractRef contractRef,
            StructuredOutputSchemaFailureClassifier failureClassifier) {
        return java.util.Objects.requireNonNull(contracts, "contracts")
                .validate(java.util.Objects.requireNonNull(contractRef, "contractRef"),
                        extractedPayload,
                        java.util.Objects.requireNonNull(
                                failureClassifier, "failureClassifier"));
    }
}
