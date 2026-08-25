package com.portfolio.agent.turn.capability.general;

/**
 * 通用知识能力不可用异常：模型未启用、截止时间已耗尽或生成链路失败时抛出，
 * 由 {@link GeneralTaskExecutor} 捕获并收敛为 FAILED(CAPABILITY_UNAVAILABLE) 终态，
 * 不向访客暴露原因链。
 */
public final class GeneralKnowledgeUnavailableException extends RuntimeException {
    public GeneralKnowledgeUnavailableException(String message) { super(message); }
    public GeneralKnowledgeUnavailableException(String message, Throwable cause) { super(message, cause); }
}
