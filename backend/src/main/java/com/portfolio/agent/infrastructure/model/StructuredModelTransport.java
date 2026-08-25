package com.portfolio.agent.infrastructure.model;

/**
 * 结构化模型传输接口：对已解析绑定执行单次模型调用的唯一入口。
 *
 * <p>契约要求实现方 fail-closed 且无修复：调用失败即抛出封闭的
 * {@link StructuredModelFailure}，由调用方决定终态；实现不得重试、
 * 不得切换 Provider、不得对失败做任何隐式回退。
 */
public interface StructuredModelTransport {
    /**
     * 对指定绑定执行一次请求（外部 HTTP 调用，可能阻塞至超时）。
     *
     * @param binding 已通过准入的传输绑定
     * @param request 已通过 Operation 策略校验的请求
     * @return content 为非空文本的模型响应
     * @throws StructuredModelFailure 任何传输、限流、鉴权或响应校验失败
     */
    StructuredModelResponse execute(
            ModelTransportBinding binding,
            StructuredModelRequest request) throws StructuredModelFailure;
}
