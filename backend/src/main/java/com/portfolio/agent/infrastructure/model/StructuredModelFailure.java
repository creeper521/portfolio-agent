package com.portfolio.agent.infrastructure.model;

/**
 * 结构化模型传输失败：传输层的封闭失败信号。
 *
 * <p>由 {@link OpenAiCompatibleStructuredModelTransport} 在调用边界抛出，
 * 只携带稳定 {@link Code}、精确 HTTP status、Retry-After/连接失败闭集与
 * 可选的限流等待秒数；不保留 HTTP body 或原始 Retry-After。Provider 异常
 * 仅作为 cause 保留，不进入公开语义。上层通常通过
 * {@link SelectedModelFailureException#from} 将其折算为对外的所选模型失败。
 */
public final class StructuredModelFailure extends RuntimeException {
    private final Code code;
    private final Integer retryAfterSeconds;
    private final Reason reason;
    private final Integer httpStatus;
    private final RetryAfterDisposition retryAfterDisposition;
    private final TransportDisposition transportDisposition;
    private final TimeoutDisposition timeoutDisposition;
    public StructuredModelFailure(Code code) {
        this(code, null, null, null, null,
                RetryAfterDisposition.NOT_APPLICABLE,
                transportDisposition(code), timeoutDisposition(code));
    }
    public StructuredModelFailure(Code code, Throwable cause) {
        this(code, null, null, cause, null,
                RetryAfterDisposition.NOT_APPLICABLE,
                transportDisposition(code), timeoutDisposition(code));
    }
    public StructuredModelFailure(Code code, Reason reason) {
        this(code, null, reason, null, null,
                RetryAfterDisposition.NOT_APPLICABLE,
                transportDisposition(code), timeoutDisposition(code));
    }
    public StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Throwable cause) {
        this(code, retryAfterSeconds, null, cause, null,
                retryAfterSeconds == null
                        ? RetryAfterDisposition.NOT_APPLICABLE
                        : RetryAfterDisposition.VALID,
                transportDisposition(code), timeoutDisposition(code));
    }
    public StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Reason reason, Throwable cause) {
        this(code, retryAfterSeconds, reason, cause, null,
                retryAfterSeconds == null
                        ? RetryAfterDisposition.NOT_APPLICABLE
                        : RetryAfterDisposition.VALID,
                transportDisposition(code), timeoutDisposition(code));
    }
    private StructuredModelFailure(
            Code code, Integer retryAfterSeconds, Reason reason,
            Throwable cause, Integer httpStatus,
            RetryAfterDisposition retryAfterDisposition,
            TransportDisposition transportDisposition,
            TimeoutDisposition timeoutDisposition) {
        super(java.util.Objects.requireNonNull(code, "code").name(), cause);
        if (retryAfterSeconds != null
                && (retryAfterSeconds < 0 || retryAfterSeconds > 300)) {
            throw new IllegalArgumentException("retryAfterSeconds is invalid");
        }
        if (retryAfterSeconds != null && code != Code.RATE_LIMITED) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds is only valid for rate limiting");
        }
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("httpStatus is invalid");
        }
        RetryAfterDisposition disposition = java.util.Objects.requireNonNull(
                retryAfterDisposition, "retryAfterDisposition");
        TransportDisposition transport = java.util.Objects.requireNonNull(
                transportDisposition, "transportDisposition");
        TimeoutDisposition timeout = java.util.Objects.requireNonNull(
                timeoutDisposition, "timeoutDisposition");
        if (disposition == RetryAfterDisposition.VALID
                && (code != Code.RATE_LIMITED || retryAfterSeconds == null)) {
            throw new IllegalArgumentException(
                    "valid Retry-After requires bounded rate limit seconds");
        }
        if ((disposition == RetryAfterDisposition.MISSING
                || disposition == RetryAfterDisposition.INVALID)
                && (code != Code.RATE_LIMITED || retryAfterSeconds != null)) {
            throw new IllegalArgumentException(
                    "Retry-After state is incompatible with failure");
        }
        if (disposition == RetryAfterDisposition.NOT_APPLICABLE
                && retryAfterSeconds != null) {
            throw new IllegalArgumentException(
                    "Retry-After seconds require a valid state");
        }
        if ((code == Code.TRANSPORT_UNAVAILABLE)
                != (transport != TransportDisposition.NOT_APPLICABLE)) {
            throw new IllegalArgumentException(
                    "transport state is incompatible with failure");
        }
        if ((code == Code.DEADLINE_EXCEEDED)
                != (timeout != TimeoutDisposition.NOT_APPLICABLE)) {
            throw new IllegalArgumentException(
                    "timeout state is incompatible with failure");
        }
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
        this.httpStatus = httpStatus;
        this.retryAfterDisposition = disposition;
        this.transportDisposition = transport;
        this.timeoutDisposition = timeout;
    }

    public static StructuredModelFailure http(
            Code code, int httpStatus) {
        return new StructuredModelFailure(
                code, null, null, null, httpStatus,
                RetryAfterDisposition.NOT_APPLICABLE,
                TransportDisposition.NOT_APPLICABLE,
                TimeoutDisposition.NOT_APPLICABLE);
    }

    public static StructuredModelFailure rateLimited(
            int httpStatus, Integer retryAfterSeconds,
            RetryAfterDisposition disposition) {
        return new StructuredModelFailure(
                Code.RATE_LIMITED, retryAfterSeconds, null, null,
                httpStatus, disposition,
                TransportDisposition.NOT_APPLICABLE,
                TimeoutDisposition.NOT_APPLICABLE);
    }

    public static StructuredModelFailure deadline(
            TimeoutDisposition disposition, Throwable cause) {
        TimeoutDisposition timeout = java.util.Objects.requireNonNull(
                disposition, "disposition");
        if (timeout == TimeoutDisposition.NOT_APPLICABLE) {
            throw new IllegalArgumentException(
                    "deadline failure requires a timeout state");
        }
        return new StructuredModelFailure(
                Code.DEADLINE_EXCEEDED, null, null, cause, null,
                RetryAfterDisposition.NOT_APPLICABLE,
                TransportDisposition.NOT_APPLICABLE, timeout);
    }

    public static StructuredModelFailure connection(Throwable cause) {
        return transport(TransportDisposition.CONNECTION, cause);
    }

    public static StructuredModelFailure cancelled(Throwable cause) {
        return transport(TransportDisposition.CANCELLED, cause);
    }

    public static StructuredModelFailure interrupted(Throwable cause) {
        return transport(TransportDisposition.INTERRUPTED, cause);
    }

    public static StructuredModelFailure transportOther(Throwable cause) {
        return transport(TransportDisposition.OTHER, cause);
    }

    private static StructuredModelFailure transport(
            TransportDisposition disposition, Throwable cause) {
        return new StructuredModelFailure(
                Code.TRANSPORT_UNAVAILABLE, null, null, cause, null,
                RetryAfterDisposition.NOT_APPLICABLE, disposition,
                TimeoutDisposition.NOT_APPLICABLE);
    }

    public Code getCode() { return code; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public Reason getReason() { return reason; }
    public Integer getHttpStatus() { return httpStatus; }
    public RetryAfterDisposition getRetryAfterDisposition() {
        return retryAfterDisposition;
    }
    public TransportDisposition getTransportDisposition() {
        return transportDisposition;
    }
    public TimeoutDisposition getTimeoutDisposition() {
        return timeoutDisposition;
    }

    private static TransportDisposition transportDisposition(Code code) {
        return code == Code.TRANSPORT_UNAVAILABLE
                ? TransportDisposition.OTHER
                : TransportDisposition.NOT_APPLICABLE;
    }

    private static TimeoutDisposition timeoutDisposition(Code code) {
        return code == Code.DEADLINE_EXCEEDED
                ? TimeoutDisposition.UNKNOWN
                : TimeoutDisposition.NOT_APPLICABLE;
    }

    /** Retry-After 只保留闭集形态，不保留原始 header 文本。 */
    public enum RetryAfterDisposition {
        NOT_APPLICABLE,
        MISSING,
        VALID,
        INVALID
    }

    /** 传输异常只保留是否属于获批连接类重试的闭集，不保留异常文本。 */
    public enum TransportDisposition {
        NOT_APPLICABLE,
        CONNECTION,
        INTERRUPTED,
        CANCELLED,
        OTHER
    }

    /** timeout 只记录是否已收到响应，不记录响应内容或异常文本。 */
    public enum TimeoutDisposition {
        NOT_APPLICABLE,
        NO_RESPONSE,
        RESPONSE_STARTED,
        UNKNOWN
    }

    /**
     * 不含 Provider 内容的低基数原因，只用于服务端诊断；不得进入公开响应。
     */
    public enum Reason {
        MALFORMED_JSON,
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
