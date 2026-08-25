package com.portfolio.agent.infrastructure.retrieval;

/**
 * 本地 embedding 失败异常：本地公开检索（BGE 路径）执行失败的封闭信号。
 *
 * <p>只携带稳定的失败 code（消息即 code），不携带文本内容或内部路径，
 * 供上层折算为公开检索不可用的终态。
 */
public final class LocalEmbeddingFailureException extends RuntimeException {

    private final String code;

    public LocalEmbeddingFailureException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}
