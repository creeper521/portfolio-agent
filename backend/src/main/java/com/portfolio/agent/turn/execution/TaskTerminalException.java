package com.portfolio.agent.turn.execution;

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
    public enum Kind { NO_RESULT, REJECTED, FAILED }
}
