package com.portfolio.agent.turn.execution;

import java.util.Objects;
import java.util.Optional;

public final class TaskOutcome {
    private final String taskId;
    private final Terminal terminal;

    public TaskOutcome(String taskId, Terminal terminal) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("taskId is invalid");
        }
        this.taskId = taskId;
        this.terminal = Objects.requireNonNull(terminal, "terminal");
    }

    public String getTaskId() { return taskId; }
    public Terminal getTerminal() { return terminal; }
    public Optional<TaskArtifact> getProducedArtifact() {
        return terminal instanceof Produced produced
                ? Optional.of(produced.getArtifact()) : Optional.empty();
    }

    public sealed interface Terminal permits Produced, ReasonTerminal {
    }

    public enum Fulfillment { FULL, PARTIAL }

    public static final class Produced implements Terminal {
        private final TaskArtifact artifact;
        private final Fulfillment fulfillment;
        public Produced(TaskArtifact artifact, Fulfillment fulfillment) {
            this.artifact = Objects.requireNonNull(artifact, "artifact");
            this.fulfillment = Objects.requireNonNull(fulfillment, "fulfillment");
        }
        public TaskArtifact getArtifact() { return artifact; }
        public Fulfillment getFulfillment() { return fulfillment; }
    }

    public abstract static sealed class ReasonTerminal implements Terminal
            permits NoResult, Rejected, Failed, Blocked, Skipped, Cancelled, TimedOut {
        private final TaskTerminalReason reason;
        protected ReasonTerminal(TaskTerminalReason reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
        }
        public TaskTerminalReason getReason() { return reason; }
    }

    public static final class NoResult extends ReasonTerminal {
        public NoResult(TaskTerminalReason reason) { super(reason); }
    }
    public static final class Rejected extends ReasonTerminal {
        public Rejected(TaskTerminalReason reason) { super(reason); }
    }
    public static final class Failed extends ReasonTerminal {
        public Failed(TaskTerminalReason reason) { super(reason); }
    }
    public static final class Blocked extends ReasonTerminal {
        public Blocked(TaskTerminalReason reason) { super(reason); }
    }
    public static final class Skipped extends ReasonTerminal {
        public Skipped(TaskTerminalReason reason) { super(reason); }
    }
    public static final class Cancelled extends ReasonTerminal {
        public Cancelled() { super(TaskTerminalReason.TURN_CANCELLED); }
    }
    public static final class TimedOut extends ReasonTerminal {
        public TimedOut() { super(TaskTerminalReason.TURN_DEADLINE_EXCEEDED); }
    }
}
