package com.portfolio.agent.evaluation.application;

/**
 * 评估运行期非受检异常：评估流水线遇到不可继续的内部状态（如数据集 cases 缺失）
 * 时抛出。它表达的是"运行无法完成"，区别于通过质量门与 {@code EvalVerdict}
 * 正常表达的质量不合格结果。
 */
public final class EvalRunException extends RuntimeException {

    public EvalRunException(String message) {
        super(message);
    }

    public EvalRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
