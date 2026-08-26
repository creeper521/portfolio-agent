package com.portfolio.agent.infrastructure.model.structured;

/** 不携带 payload 或 validator 原始消息的结构化输出安全失败。 */
public final class StructuredOutputValidationException extends IllegalArgumentException {
    private final Reason reason;
    private final String diagnosticReason;
    private final Stage stage;

    public StructuredOutputValidationException(Reason reason) {
        this(reason, java.util.Objects.requireNonNull(reason, "reason").name(),
                Stage.UNCLASSIFIED_SCHEMA);
    }

    public StructuredOutputValidationException(Reason reason, String diagnosticReason) {
        this(reason, diagnosticReason, Stage.UNCLASSIFIED_SCHEMA);
    }

    public StructuredOutputValidationException(
            Reason reason, String diagnosticReason, Stage stage) {
        super("structured output validation failed");
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
        if (diagnosticReason == null
                || !diagnosticReason.matches("^[A-Z0-9_]{1,96}$")) {
            throw new IllegalArgumentException("diagnosticReason is invalid");
        }
        this.diagnosticReason = diagnosticReason;
        this.stage = java.util.Objects.requireNonNull(stage, "stage");
    }

    public Reason getReason() {
        return reason;
    }

    public String getDiagnosticReason() {
        return diagnosticReason;
    }

    public Stage getStage() { return stage; }

    public StructuredOutputValidationException atStage(Stage value) {
        if (stage == value) return this;
        return new StructuredOutputValidationException(
                reason, diagnosticReason, value);
    }

    public enum Stage {
        UNCLASSIFIED_SCHEMA,
        PROVIDER_DRAFT_SCHEMA,
        DETERMINISTIC_COMPILER,
        CANONICAL_SCHEMA
    }

    public enum Reason {
        INVALID_JSON,
        OUTPUT_TOO_LARGE,
        UNSUPPORTED_ROOT_KIND,
        CLARIFICATION_BLOCKED_GOAL_REQUIRED,
        MISSING_REQUIRED_FIELD,
        UNKNOWN_FIELD,
        FIELD_TYPE_INVALID,
        FIELD_VALUE_INVALID,
        VARIANT_SHAPE_INVALID,
        ARRAY_CONSTRAINT_INVALID,
        STRING_CONSTRAINT_INVALID,
        NUMBER_CONSTRAINT_INVALID,
        LOCAL_SCHEMA_REJECTED,
        DRAFT_REQUIRED_FIELD_MISSING,
        DRAFT_BRANCH_INVALID,
        DRAFT_FIELD_CONFLICT,
        DRAFT_ANCHOR_NOT_FOUND,
        DRAFT_ANCHOR_AMBIGUOUS,
        DRAFT_SUBJECT_OUTSIDE_PUBLIC_SCOPE,
        DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE
    }
}
