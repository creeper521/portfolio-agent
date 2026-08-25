package com.portfolio.agent.common.exception;

import java.util.Objects;

/**
 * 应用层统一业务异常：携带稳定的机器可读 {@link ErrorCode} 供上层（如全局异常处理器）映射为对外响应。
 *
 * <p>消息与错误码由抛出方保证不含内部实现细节（路径、主机、栈轨迹等），可安全进入公开 API 响应；
 * 可选的 retryAfterSeconds 用于向客户端提示建议重试间隔（秒）。</p>
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Integer retryAfterSeconds;

    /**
     * 以指定错误码与消息构造异常，不携带重试间隔提示。
     */
    public ApplicationException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 以指定错误码、消息与建议重试间隔构造异常。
     *
     * @param errorCode        稳定错误码，不允许为 null
     * @param message          异常消息，不允许为 null，须已脱敏可直接对外展示
     * @param retryAfterSeconds 建议客户端等待的重试秒数；null 表示不提示，非 null 时必须为正数
     * @throws IllegalArgumentException 当 retryAfterSeconds 非null且小于 1 时抛出
     */
    public ApplicationException(
            ErrorCode errorCode,
            String message,
            Integer retryAfterSeconds
    ) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (retryAfterSeconds != null && retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
