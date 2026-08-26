package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;

import java.util.Objects;

/** 已批准的 Operation 与 wire schema 版本引用。 */
public record StructuredContractRef(ModelOperation operation, String schemaVersion) {
    public StructuredContractRef {
        Objects.requireNonNull(operation, "operation");
        if (schemaVersion == null || schemaVersion.isBlank() || schemaVersion.length() > 96) {
            throw new IllegalArgumentException("schemaVersion is invalid");
        }
        schemaVersion = schemaVersion.strip();
    }
}
