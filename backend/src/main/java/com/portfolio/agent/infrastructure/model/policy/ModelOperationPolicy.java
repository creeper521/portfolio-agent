package com.portfolio.agent.infrastructure.model.policy;

import java.time.Duration;
import java.util.Objects;

/** Immutable startup authority for one model-backed operation. */
public final class ModelOperationPolicy {
    private final ModelOperation operation;
    private final OperationMode mode;
    private final String schemaVersion;
    private final int maxOutputTokens;
    private final Duration timeout;

    public ModelOperationPolicy(
            ModelOperation operation,
            OperationMode mode,
            String schemaVersion,
            int maxOutputTokens,
            Duration timeout) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.schemaVersion = schemaVersion;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
    }

    public ModelOperation getOperation() { return operation; }
    public OperationMode getMode() { return mode; }
    public String getSchemaVersion() { return schemaVersion; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public Duration getTimeout() { return timeout; }

    public OperationReadiness readiness() {
        if (mode == OperationMode.DISABLED) {
            return OperationReadiness.DISABLED;
        }
        if (schemaVersion == null
                || schemaVersion.isBlank()
                || maxOutputTokens < 1
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()) {
            return OperationReadiness.INCOMPLETE_CONFIGURATION;
        }
        return OperationReadiness.AVAILABLE_WITH_PROVIDER;
    }

    public void validateStartup() {
        if (mode == OperationMode.ENABLED
                && readiness() == OperationReadiness.INCOMPLETE_CONFIGURATION) {
            throw new IllegalStateException(
                    "enabled model operation is incomplete: " + operation);
        }
    }
}
