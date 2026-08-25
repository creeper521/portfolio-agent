package com.portfolio.agent.turn.execution;

import java.util.Objects;
import java.util.Optional;

/**
 * 单个任务的终态判定，不可变：taskId 绑定一个 {@link Terminal}。
 * 这是 Execution 层的结果词汇表——所有控制流（正常产出、无结果、拒绝、失败、
 * 阻塞、跳过、取消、超时）都折叠为 Terminal 的封闭子类型，Engine 不再传播异常。
 * taskId 格式在构造时校验（字母数字与 . _ - ，长度 1..128）。
 */
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

    /** 终态标记接口：sealed 封闭为"成功产出"与"带原因的非产出"两大族。 */
    public sealed interface Terminal permits Produced, ReasonTerminal {
    }

    /** 满足度等级：FULL 完全满足任务目标，PARTIAL 部分满足（仍算有效产出）。 */
    public enum Fulfillment { FULL, PARTIAL }

    /** 成功终态：携带产出物与满足度，是唯一可向下游传递语义数据的终态。 */
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

    /** 非产出终态的公共基类：统一携带一个 {@link TaskTerminalReason}，封闭为七种具体终态。 */
    public abstract static sealed class ReasonTerminal implements Terminal
            permits NoResult, Rejected, Failed, Blocked, Skipped, Cancelled, TimedOut {
        private final TaskTerminalReason reason;
        protected ReasonTerminal(TaskTerminalReason reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
        }
        public TaskTerminalReason getReason() { return reason; }
    }

    /** 受控的无结果终态：能力正常运转但对本任务没有可支持的结果。 */
    public static final class NoResult extends ReasonTerminal {
        public NoResult(TaskTerminalReason reason) { super(reason); }
    }
    /** 拒绝终态：任务输入不满足约束而被主动拒绝（如输入不合法）。 */
    public static final class Rejected extends ReasonTerminal {
        public Rejected(TaskTerminalReason reason) { super(reason); }
    }
    /** 失败终态：执行尝试后失败（含能力缺失、执行异常等）。 */
    public static final class Failed extends ReasonTerminal {
        public Failed(TaskTerminalReason reason) { super(reason); }
    }
    /** 阻塞终态：入边存在但没有任何可传递的依赖数据，执行已无意义。 */
    public static final class Blocked extends ReasonTerminal {
        public Blocked(TaskTerminalReason reason) { super(reason); }
    }
    /** 跳过终态：任务被计划层主动跳过、未进入执行。 */
    public static final class Skipped extends ReasonTerminal {
        public Skipped(TaskTerminalReason reason) { super(reason); }
    }
    /** 取消终态：Turn 级取消信号触发时对未完任务的统一兜底，原因固定为 TURN_CANCELLED。 */
    public static final class Cancelled extends ReasonTerminal {
        public Cancelled() { super(TaskTerminalReason.TURN_CANCELLED); }
    }
    /** 超时终态：Turn 截止时间耗尽时对未完任务的统一兜底，原因固定为 TURN_DEADLINE_EXCEEDED。 */
    public static final class TimedOut extends ReasonTerminal {
        public TimedOut() { super(TaskTerminalReason.TURN_DEADLINE_EXCEEDED); }
    }
}
