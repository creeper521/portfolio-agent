package com.portfolio.agent.turn.execution;

/**
 * Typed, non-rendered semantic value that may flow across a real data edge.
 *
 * <p>跨真实数据依赖边传递的类型化语义值标记接口：携带结构化语义而非渲染文本，
 * 只有 {@link TaskOutcome.Produced} 中的该类结果会被下游任务作为输入消费。
 */
public interface TaskSemanticResult {
}
