package com.portfolio.agent.common.exception;

/**
 * 错误码契约：为对外 API 错误响应提供稳定标识、默认文案与 HTTP 状态映射。
 *
 * <p>实现方必须保证 code 稳定不变（客户端可能依赖它做分支），defaultMessage 为可直接公开展示的
 * 中文文案，不得包含路径、主机、栈轨迹等内部信息。</p>
 */
public interface ErrorCode {

    /**
     * 返回稳定的机器可读错误码（如 VALIDATION_ERROR），同一错误的 code 永不改变。
     */
    String getCode();

    /**
     * 返回可直接对外展示的默认中文文案，已按信息不泄漏边界收敛。
     */
    String getDefaultMessage();

    /**
     * 返回该错误映射到的 HTTP 响应状态码。
     */
    int getHttpStatus();
}
