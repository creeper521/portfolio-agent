package com.portfolio.agent.infrastructure.model.structured;

/** Provider 请求的输出 token 字段策略；不允许环境自由指定字段名。 */
public enum TokenFieldPolicy {
    MAX_TOKENS,
    OMIT
}
