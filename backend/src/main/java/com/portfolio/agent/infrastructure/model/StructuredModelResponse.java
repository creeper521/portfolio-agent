package com.portfolio.agent.infrastructure.model;

/** 结构化模型响应：模型返回的非空文本（按约定为待上层校验的 JSON 字符串）。 */
public record StructuredModelResponse(String json) {
    public StructuredModelResponse {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("json is required");
    }
}
