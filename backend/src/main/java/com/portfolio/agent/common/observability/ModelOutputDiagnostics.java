package com.portfolio.agent.common.observability;

import java.util.Objects;
import java.util.Map;
import java.util.Set;

/** Publishes only closed rejection metadata; model output and exception text are excluded. */
public final class ModelOutputDiagnostics {
    private static final Map<String, Set<String>> RETRY_WAITS_BY_FAILURE =
            Map.of(
                    "DEADLINE_EXCEEDED", Set.of("NO_WAIT"),
                    "TRANSPORT_UNAVAILABLE", Set.of("NO_WAIT"),
                    "PROVIDER_UNAVAILABLE", Set.of("NO_WAIT"),
                    "RATE_LIMITED", Set.of(
                            "JITTER_100_250_MS", "RETRY_AFTER_LE_1S"));

    private final DiagnosticEventPublisher publisher;

    public ModelOutputDiagnostics(DiagnosticEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public void rejected(String operation, Layer layer) {
        rejected(operation, layer, null);
    }

    public void rejected(String operation, Layer layer, String reason) {
        try {
            if (reason != null && !reason.matches("^[A-Z0-9_]{1,96}$")) {
                throw new IllegalArgumentException("reason must be a closed value");
            }
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(
                            "provider.output.rejected", DiagnosticLevel.WARN)
                    .field("provider.operation", operation)
                    .field("failure.layer", layer)
                    .field("failure.code", failureCode(layer));
            if (reason != null) {
                event.field("failure.reason", reason);
            }
            publisher.publish(event.build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    /** 发布已采纳输出的闭集准入等级与规则计数，不接受字段名、正文或自由文本。 */
    public void admitted(
            String operation, AdmissionLevel level,
            NormalizationRule rule, int count) {
        try {
            Objects.requireNonNull(level, "level");
            if (count < 1 || count > 100) {
                throw new IllegalArgumentException("normalization count is invalid");
            }
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(
                            "provider.output.admitted",
                            level == AdmissionLevel.DEGRADED
                                    ? DiagnosticLevel.WARN : DiagnosticLevel.INFO)
                    .field("provider.operation", operation)
                    .field("admission.level", level);
            if (rule != null) {
                event.field("normalization.rule", rule)
                        .field("normalization.count", count);
            }
            publisher.publish(event.build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    /**
     * Publishes only the bounded decision to schedule General's second provider attempt.
     * Provider identity, attempt UUIDs, response content and exception text are excluded.
     */
    public void retryScheduled(
            int attemptIndex, int attemptCount,
            String failureCode, String waitBucket) {
        try {
            if (attemptIndex != 2 || attemptCount != 2) {
                throw new IllegalArgumentException(
                        "only the approved second attempt may be scheduled");
            }
            Set<String> approvedWaits = RETRY_WAITS_BY_FAILURE.get(
                    failureCode);
            if (approvedWaits == null) {
                throw new IllegalArgumentException(
                        "retry failure code must be a closed value");
            }
            if (!approvedWaits.contains(waitBucket)) {
                throw new IllegalArgumentException(
                        "retry failure and wait combination is impossible");
            }
            publisher.publish(DiagnosticEvent.builder(
                            "provider.call.retry_scheduled", DiagnosticLevel.INFO)
                    .field("attempt.index", attemptIndex)
                    .field("attempt.count", attemptCount)
                    .field("failure.code", failureCode)
                    .field("wait.bucket", waitBucket)
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    public static ModelOutputDiagnostics none() {
        return new ModelOutputDiagnostics(event -> { });
    }

    private String failureCode(Layer layer) {
        return switch (layer) {
            case PROVIDER_DRAFT_SCHEMA, CANONICAL_SCHEMA, SCHEMA ->
                    "OUTPUT_SCHEMA_REJECTED";
            case DETERMINISTIC_COMPILER -> "OUTPUT_COMPILER_REJECTED";
            case SEMANTIC -> "OUTPUT_SEMANTIC_REJECTED";
        };
    }

    public enum Layer {
        PROVIDER_DRAFT_SCHEMA,
        DETERMINISTIC_COMPILER,
        CANONICAL_SCHEMA,
        SCHEMA,
        SEMANTIC
    }

    public enum AdmissionLevel {
        EXACT,
        NORMALIZED,
        DEGRADED
    }

    public enum NormalizationRule {
        TRIM_TEXT,
        COLLAPSE_MEANINGLESS_WHITESPACE,
        UNICODE_NORMALIZE_NFC,
        WRAP_STRING_AS_ARRAY,
        JOIN_ROLE_SENTENCES,
        NORMALIZE_TERMINAL_PUNCTUATION,
        MISSING_CAVEATS_AS_EMPTY,
        DROPPED_INVALID_OPTIONAL_CAVEATS,
        UNKNOWN_FIELD_COUNT
    }
}
