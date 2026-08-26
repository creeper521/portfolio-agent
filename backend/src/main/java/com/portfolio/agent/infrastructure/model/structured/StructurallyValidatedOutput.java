package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/** 通过严格 JSON parser 与 canonical schema 后的唯一领域输入。 */
public record StructurallyValidatedOutput(
        StructuredContractRef contractRef,
        String contractFingerprint,
        JsonNode jsonTree) {
    public StructurallyValidatedOutput {
        Objects.requireNonNull(contractRef, "contractRef");
        Objects.requireNonNull(jsonTree, "jsonTree");
        if (!jsonTree.isObject()) {
            throw new IllegalArgumentException("validated output root must be an object");
        }
        jsonTree = jsonTree.deepCopy();
        if (contractFingerprint == null || contractFingerprint.length() != 64) {
            throw new IllegalArgumentException("contractFingerprint is invalid");
        }
    }

    @Override public JsonNode jsonTree() {
        return jsonTree.deepCopy();
    }
}
