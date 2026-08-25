package com.portfolio.agent.turn.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Agent API 的统一错误响应体：requestId + 错误详情（code/message/retryable/
 * retryAfterSeconds）。message 是面向公众的固定中文文案，不含内部细节；
 * null 字段（如未认证时的 requestId）不序列化。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentApiErrorResponse(UUID requestId, Error error) {
    /** 错误详情：稳定 code、公众文案、是否可重试与建议等待秒数。 */
    public record Error(
            String code, String message, boolean retryable,
            Long retryAfterSeconds) { }
    /** 错误响应工厂。 */
    public static AgentApiErrorResponse of(
            UUID requestId, String code, String message,
            boolean retryable, Long retryAfterSeconds) {
        return new AgentApiErrorResponse(
                requestId, new Error(code, message, retryable, retryAfterSeconds));
    }
}
