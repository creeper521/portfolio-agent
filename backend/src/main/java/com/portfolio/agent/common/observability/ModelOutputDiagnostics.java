package com.portfolio.agent.common.observability;

import java.util.Objects;

/** Publishes only closed rejection metadata; model output and exception text are excluded. */
public final class ModelOutputDiagnostics {
    private final DiagnosticEventPublisher publisher;

    public ModelOutputDiagnostics(DiagnosticEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public void rejected(String operation, Layer layer) {
        try {
            publisher.publish(DiagnosticEvent.builder(
                            "provider.output.rejected", DiagnosticLevel.WARN)
                    .field("provider.operation", operation)
                    .field("failure.layer", layer)
                    .field("failure.code", layer == Layer.SCHEMA
                            ? "OUTPUT_SCHEMA_REJECTED"
                            : "OUTPUT_SEMANTIC_REJECTED")
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    public static ModelOutputDiagnostics none() {
        return new ModelOutputDiagnostics(event -> { });
    }

    public enum Layer { SCHEMA, SEMANTIC }
}
