package com.portfolio.agent.infrastructure.model;

/**
 * 结构化模型传输失败：传输层的封闭失败信号。
 *
 * <p>由 {@link OpenAiCompatibleStructuredModelTransport} 在调用边界抛出，
 * 只携带稳定 {@link Code} 与可选的限流等待秒数；Provider 原始异常仅作为
 * cause 保留，供上层日志使用，不进入公开语义。上层通常通过
 * {@link SelectedModelFailureException#from} 将其折算为对外的所选模型失败。
 */
public final class StructuredModelFailure extends RuntimeException {
    private final Code code;
    private final Integer retryAfterSeconds;
    private final Reason reason;
    public StructuredModelFailure(Code code) { this(code, null, null, null); }
    public StructuredModelFailure(Code code, Throwable cause) {
        this(code, null, null, cause);
    }
    public StructuredModelFailure(Code code, Reason reason) {
        this(code, null, reason, null);
    }
    public StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Throwable cause) {
        this(code, retryAfterSeconds, null, cause);
    }
    public StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Reason reason, Throwable cause) {
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
        this.reason = reason;
    }
    public Code getCode() { return code; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public Reason getReason() { return reason; }

    /**
     * 不含 Provider 内容的低基数原因，只用于服务端诊断；不得进入公开响应。
     */
    public enum Reason {
        CHOICES_CARDINALITY,
        FINISH_REASON,
        MESSAGE_SHAPE,
        REFUSAL,
        UNEXPECTED_TOOL_CARRIER,
        CONTENT_MISSING,
        TOOL_CALL_CARDINALITY,
        TOOL_CALL_TYPE,
        TOOL_FUNCTION,
        TOOL_ARGUMENTS
    }
    /**
     * 封闭失败码，每个码标注所属失败层（getLayer）：
     * TRANSPORT 为网络/HTTP 层，JSON 为响应体解析层，
     * ENVELOPE 为 OpenAI 兼容信封校验层，SEMANTIC 为响应语义校验层。
     */
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
