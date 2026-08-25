package com.portfolio.agent.turn.execution;

/**
 * Internal presentation owned by the producing task; never used as dependency input.
 *
 * <p>任务产出的内部展示片段标记接口：只描述"如何向访客呈现"，本身不是
 * 可跨依赖边传递的数据；跨边传递的语义数据统一走 {@link TaskSemanticResult}。
 */
public interface TaskPresentation {
}
