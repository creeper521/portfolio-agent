package com.portfolio.agent.infrastructure.model;

public final class StructuredModelFailure extends RuntimeException {
    private final Code code;
    public StructuredModelFailure(Code code) { this(code, null); }
    public StructuredModelFailure(Code code, Throwable cause) {
        super(code.name(), cause); this.code = code;
    }
    public Code getCode() { return code; }
    public enum Code { DEADLINE_EXCEEDED, TRANSPORT_UNAVAILABLE, PROVIDER_REJECTED, INVALID_RESPONSE }
}
