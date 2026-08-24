package com.portfolio.agent.infrastructure.model;

public final class StructuredModelFailure extends RuntimeException {
    private final Code code;
    public StructuredModelFailure(Code code) { this(code, null); }
    public StructuredModelFailure(Code code, Throwable cause) {
        super(code.name(), cause); this.code = code;
    }
    public Code getCode() { return code; }
    public enum Code {
        DEADLINE_EXCEEDED("TRANSPORT"),
        TRANSPORT_UNAVAILABLE("TRANSPORT"),
        AUTHENTICATION_REJECTED("TRANSPORT"),
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
