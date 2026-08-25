package com.portfolio.agent.portfolio.exception;

/**
 * 公开快照校验失败异常：快照结构、引用或口径不满足发布契约时抛出。
 *
 * <p>属于数据/配置类错误而非访客输入错误：加载公开快照时一旦校验不通过即快速失败
 * （fail-fast），阻止不合规内容进入运行时与对外响应。消息只包含字段级问题描述，
 * 不携带原始证据或私有数据。
 */
public class InvalidPortfolioSnapshotException extends RuntimeException {

    public InvalidPortfolioSnapshotException(String message) {
        super(message);
    }

    public InvalidPortfolioSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
