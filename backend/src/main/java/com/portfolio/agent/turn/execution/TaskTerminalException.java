package com.portfolio.agent.turn.execution;

/**
 * 任务主动报告终态的业务异常：Executor 用它向 Engine 声明"本任务以受控方式结束"，
 * 而非抛出未预期异常。Engine 按 {@link Kind} 把它映射为对应的
 * {@link TaskOutcome} 终态（NoResult / Rejected / Failed），不视为执行器故障。
 */
public final class TaskTerminalException extends RuntimeException {
    private final Kind kind;
    private final TaskTerminalReason reason;

    public TaskTerminalException(Kind kind, TaskTerminalReason reason) {
        super(kind.name() + ":" + reason.name());
        this.kind = kind;
        this.reason = reason;
    }

    public Kind getKind() { return kind; }
    public TaskTerminalReason getReason() { return reason; }
    /** 终态类别：NO_RESULT 无可用结果、REJECTED 拒绝输入、FAILED 执行失败。 */
    public enum Kind { NO_RESULT, REJECTED, FAILED }
}
