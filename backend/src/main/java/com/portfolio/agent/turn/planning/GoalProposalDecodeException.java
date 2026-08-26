package com.portfolio.agent.turn.planning;

/**
 * Goal proposal 的闭集解码失败；只承载可安全记录的内部原因。
 *
 * <p>异常消息仅用于本地调试，诊断层必须读取 {@link #getReason()}，
 * 不得解析消息推断原因。</p>
 */
public final class GoalProposalDecodeException extends IllegalArgumentException {
    private final Reason reason;

    public GoalProposalDecodeException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason getReason() {
        return reason;
    }

    /** 当前已有真实样本证明需要公开到安全诊断面的解码原因。 */
    public enum Reason {
        CLARIFICATION_BLOCKED_GOAL_REQUIRED,
        UNSUPPORTED_ROOT_KIND
    }
}
