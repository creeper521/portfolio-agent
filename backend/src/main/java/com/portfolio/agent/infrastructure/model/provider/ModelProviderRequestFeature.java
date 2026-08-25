package com.portfolio.agent.infrastructure.model.provider;

/**
 * 请求侧特征封闭枚举：描述传输适配层按配置发送的请求形态
 * （JSON object、思考禁用、非流式）。
 *
 * <p>注意这些只是"适配层声明了什么请求"，并非 Provider 实际行为的在线证据。
 */
public enum ModelProviderRequestFeature {
    JSON_OBJECT_REQUEST,
    THINKING_DISABLED_REQUEST,
    NON_STREAMING_REQUEST
}
