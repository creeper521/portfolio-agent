package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 通过严格 JSON parser、资源护栏与 canonical schema 后的 opaque capability。
 * 只有本 package 的 Registry 能签发，调用方只能读取防御性投影。
 */
public final class StructurallyValidatedOutput {
    private final StructuredContractRef contractRef;
    private final String contractFingerprint;
    private final JsonNode jsonTree;

    StructurallyValidatedOutput(
            StructuredContractRef contractRef,
            String contractFingerprint,
            JsonNode jsonTree) {
        this.contractRef = Objects.requireNonNull(contractRef, "contractRef");
        Objects.requireNonNull(jsonTree, "jsonTree");
        if (!jsonTree.isObject()) {
            throw new IllegalArgumentException("validated output root must be an object");
        }
        if (contractFingerprint == null || contractFingerprint.length() != 64) {
            throw new IllegalArgumentException("contractFingerprint is invalid");
        }
        this.contractFingerprint = contractFingerprint;
        this.jsonTree = jsonTree.deepCopy();
    }

    public StructuredContractRef contractRef() {
        return contractRef;
    }

    public String contractFingerprint() {
        return contractFingerprint;
    }

    public JsonNode jsonTree() {
        return jsonTree.deepCopy();
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof StructurallyValidatedOutput other)) return false;
        return contractRef.equals(other.contractRef)
                && contractFingerprint.equals(other.contractFingerprint)
                && jsonTree.equals(other.jsonTree);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractRef, contractFingerprint, jsonTree);
    }

    @Override
    public String toString() {
        return "StructurallyValidatedOutput[contractRef=" + contractRef
                + ", contractFingerprint=" + contractFingerprint + "]";
    }
}
