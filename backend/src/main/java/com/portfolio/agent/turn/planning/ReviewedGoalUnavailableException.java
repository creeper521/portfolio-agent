package com.portfolio.agent.turn.planning;

/**
 * 已审核目标不可用：预设命令无法在当前审核快照中解析时抛出，
 * 由 {@link GoalResolver} 收敛为 CAPABILITY_UNAVAILABLE 固定文案。
 */
public final class ReviewedGoalUnavailableException extends RuntimeException {
    public ReviewedGoalUnavailableException(String message) {
        super(message);
    }
}
