package com.portfolio.agent.infrastructure.model;

/** Closed request-time model selection failure. It never carries provider detail. */
public final class ModelExecutionResolutionException extends RuntimeException {
    private final Code code;

    public ModelExecutionResolutionException(Code code) {
        super("model execution selection cannot be resolved");
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        SELECTED_MODEL_UNAVAILABLE,
        MODEL_SELECTION_STALE
    }
}
