package com.portfolio.agent.infrastructure.model;

/**
 * 显式所选模型的操作失败异常：面向上层的封闭、provider 中立失败。
 *
 * <p>由 {@link StructuredModelFailure}（传输层失败）折算而来，只保留稳定的
 * {@link Code}、是否可重试、限流等待秒数与"是否真正发起过调用"标记；
 * Provider 原始细节只留在 cause 链中，公开投影可以安全消费其字段。
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

    /**
     * 把传输层失败折算为面向上层的失败。
     *
     * <p>映射规则：截止耗尽/传输与 Provider 不可用 → 可重试的临时不可用；
     * 限流 → 可重试并携带夹取后的等待秒数；响应过大/JSON/信封/语义非法 →
     * 不可重试的无效响应；鉴权、计费与 Provider 拒绝 → 不可重试的不可用。
     *
     * @param failure 传输层失败，不允许为 null
     */
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

    /** 调用前即判定不可用（如未发起 HTTP 就被准入拒绝）：attempted = false。 */
    public static SelectedModelFailureException unavailableBeforeAttempt() {
        return unavailable(false, null);
    }

    /** 调用前即判定临时不可用（如启动准入尚未就绪）：可重试、attempted = false。 */
    public static SelectedModelFailureException temporarilyUnavailableBeforeAttempt() {
        return new SelectedModelFailureException(
                Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                true, null, false, null);
    }

    /**
     * 构造"已调用但响应无效"的失败：不可重试、attempted = true。
     *
     * @param cause 原始成因，仅留在异常链中
     */
    public static SelectedModelFailureException invalidResponse(Throwable cause) {
        return new SelectedModelFailureException(
                Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null, true, cause);
    }

    /** 限流等待秒数：传输层未提供时使用传输层的缺省值。 */
    private static int rateLimitRetryAfterSeconds(
            StructuredModelFailure source) {
        Integer retryAfterSeconds = source.getRetryAfterSeconds();
        return retryAfterSeconds == null
                ? OpenAiCompatibleStructuredModelTransport
                        .DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS
                : Math.max(1, retryAfterSeconds);
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

    /** 封闭的所选模型失败码：公开投影可安全消费。 */
    public enum Code {
        /** 所选模型不可用（鉴权/计费/Provider 拒绝等），不可重试。 */
        SELECTED_MODEL_UNAVAILABLE,
        /** 所选模型临时不可用（超时、传输或 Provider 故障），可重试。 */
        SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
        /** 所选模型被限流，可按 retryAfterSeconds 等待后重试。 */
        SELECTED_MODEL_RATE_LIMITED,
        /** 所选模型返回了无效响应，不可重试。 */
        SELECTED_MODEL_INVALID_RESPONSE
    }
}
