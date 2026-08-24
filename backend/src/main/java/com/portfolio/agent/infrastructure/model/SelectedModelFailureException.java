package com.portfolio.agent.infrastructure.model;

/**
 * Closed, provider-neutral failure of the explicitly selected model.
 * Public projection may consume its fields; Provider detail stays only in the cause chain.
 */
public final class SelectedModelFailureException extends RuntimeException {
    private final Code code;
    private final boolean retryable;
    private final Integer retryAfterSeconds;
    private final boolean attempted;

    private SelectedModelFailureException(
            Code code,
            boolean retryable,
            Integer retryAfterSeconds,
            boolean attempted,
            Throwable cause) {
        super("selected model operation failed", cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
        if (retryAfterSeconds != null
                && (retryAfterSeconds < 1 || retryAfterSeconds > 300)) {
            throw new IllegalArgumentException("retryAfterSeconds is invalid");
        }
        if (retryAfterSeconds != null && code != Code.SELECTED_MODEL_RATE_LIMITED) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds is only valid for rate limiting");
        }
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
        this.attempted = attempted;
    }

    public static SelectedModelFailureException from(
            StructuredModelFailure failure) {
        StructuredModelFailure source = java.util.Objects.requireNonNull(
                failure, "failure");
        return switch (source.getCode()) {
            case DEADLINE_EXCEEDED, TRANSPORT_UNAVAILABLE, PROVIDER_UNAVAILABLE ->
                    new SelectedModelFailureException(
                            Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                            true, null, true, source);
            case RATE_LIMITED -> new SelectedModelFailureException(
                    Code.SELECTED_MODEL_RATE_LIMITED,
                    true, rateLimitRetryAfterSeconds(source),
                    true, source);
            case RESPONSE_TOO_LARGE, RESPONSE_JSON_INVALID,
                 RESPONSE_ENVELOPE_INVALID, INVALID_RESPONSE ->
                    invalidResponse(source);
            case AUTHENTICATION_REJECTED, BILLING_REJECTED, PROVIDER_REJECTED ->
                    unavailable(true, source);
        };
    }

    public static SelectedModelFailureException unavailableBeforeAttempt() {
        return unavailable(false, null);
    }

    public static SelectedModelFailureException temporarilyUnavailableBeforeAttempt() {
        return new SelectedModelFailureException(
                Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                true, null, false, null);
    }

    public static SelectedModelFailureException invalidResponse(Throwable cause) {
        return new SelectedModelFailureException(
                Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null, true, cause);
    }

    private static int rateLimitRetryAfterSeconds(
            StructuredModelFailure source) {
        return source.getRetryAfterSeconds() == null
                ? OpenAiCompatibleStructuredModelTransport
                        .DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS
                : source.getRetryAfterSeconds();
    }

    private static SelectedModelFailureException unavailable(
            boolean attempted, Throwable cause) {
        return new SelectedModelFailureException(
                Code.SELECTED_MODEL_UNAVAILABLE,
                false, null, attempted, cause);
    }

    public Code getCode() { return code; }
    public boolean isRetryable() { return retryable; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public boolean isAttempted() { return attempted; }

    public enum Code {
        SELECTED_MODEL_UNAVAILABLE,
        SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
        SELECTED_MODEL_RATE_LIMITED,
        SELECTED_MODEL_INVALID_RESPONSE
    }
}
