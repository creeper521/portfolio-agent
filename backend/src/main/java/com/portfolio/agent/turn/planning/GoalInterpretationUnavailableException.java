package com.portfolio.agent.turn.planning;

/**
 * 目标解释能力不可用：解释端口（模型或 fail-closed 模板路径）无法给出
 * 可信结果时抛出。
 *
 * <p>{@link GoalResolver} 会将其收敛为 CAPABILITY_UNAVAILABLE 固定文案，
 * 不让失败传播到访客侧。</p>
 */
public final class GoalInterpretationUnavailableException extends RuntimeException {
    public GoalInterpretationUnavailableException() {
        super("goal interpretation is unavailable");
    }

    public GoalInterpretationUnavailableException(Throwable cause) {
        super("goal interpretation is unavailable", cause);
    }
}
