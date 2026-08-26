package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;

import java.util.Objects;

/** 编译完成的仓库内 canonical output contract。 */
public record StructuredOutputContract(
        StructuredContractRef ref,
        String outputName,
        JsonNode canonicalSchema,
        String contractFingerprint,
        Schema validator) {
    public StructuredOutputContract {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(canonicalSchema, "canonicalSchema");
        Objects.requireNonNull(validator, "validator");
        canonicalSchema = canonicalSchema.deepCopy();
        if (outputName == null || outputName.isBlank()) {
            throw new IllegalArgumentException("outputName is required");
        }
        if (contractFingerprint == null || contractFingerprint.length() != 64) {
            throw new IllegalArgumentException("contractFingerprint is invalid");
        }
    }

    @Override public JsonNode canonicalSchema() {
        return canonicalSchema.deepCopy();
    }
}
