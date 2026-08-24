package com.portfolio.agent.infrastructure.model;

public final class StructuredModelFailure extends RuntimeException {
    private final Code code;
    private final Integer retryAfterSeconds;
    public StructuredModelFailure(Code code) { this(code, null, null); }
    public StructuredModelFailure(Code code, Throwable cause) {
        this(code, null, cause);
    }
    public StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Throwable cause) {
        super(java.util.Objects.requireNonNull(code, "code").name(), cause);
        if (retryAfterSeconds != null
                && (retryAfterSeconds < 1 || retryAfterSeconds > 300)) {
            throw new IllegalArgumentException("retryAfterSeconds is invalid");
        }
        if (retryAfterSeconds != null && code != Code.RATE_LIMITED) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds is only valid for rate limiting");
        }
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public Code getCode() { return code; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public enum Code {
        DEADLINE_EXCEEDED("TRANSPORT"),
        TRANSPORT_UNAVAILABLE("TRANSPORT"),
        AUTHENTICATION_REJECTED("TRANSPORT"),
        BILLING_REJECTED("TRANSPORT"),
        RATE_LIMITED("TRANSPORT"),
        PROVIDER_UNAVAILABLE("TRANSPORT"),
        PROVIDER_REJECTED("TRANSPORT"),
        RESPONSE_TOO_LARGE("TRANSPORT"),
        RESPONSE_JSON_INVALID("JSON"),
        RESPONSE_ENVELOPE_INVALID("ENVELOPE"),
        INVALID_RESPONSE("SEMANTIC");

        private final String layer;

        Code(String layer) {
            this.layer = layer;
        }

        public String getLayer() {
            return layer;
        }
    }
}
