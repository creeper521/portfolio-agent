package com.portfolio.agent.turn.capability.portfolio.retrieval;

/** 检索失败的机器可读分类，用于决定是否降级重试以及如何对外呈现失败。 */
public enum RetrievalAttemptFailure {
    /** 向量检索扩展不可用，可降级到全文检索。 */
    VECTOR_UNAVAILABLE,
    /** 检索后端连接不可用。 */
    BACKEND_CONNECTION_UNAVAILABLE,
    /** 检索在 Turn 截止时间内未完成。 */
    BACKEND_TIMEOUT,
    /** 调用方在截止前取消。 */
    CANCELLED,
    /** 候选与请求的 contentReleaseId 不一致，禁止跨快照混合。 */
    CONTENT_RELEASE_MISMATCH,
    /** 公开内容完整性校验失败。 */
    INTEGRITY_FAILURE,
    /** 检索请求本身不合法（如主体范围为空）。 */
    INVALID_REQUEST
}
