package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;

/**
 * 语义任务执行器 SPI：每种 {@link SemanticTask.SourceDomain} 恰有一个实现，
 * 由 {@link SemanticTurnEngine} 按 SourceDomain 分发任务。
 *
 * <p>实现约定：正常结束返回 {@link TaskExecutionResult}；受控结束抛
 * {@link TaskTerminalException}；只有 {@code SelectedModelFailureException}
 * 允许原样上抛（整轮 fail-closed），其余 RuntimeException 会被 Engine 兜底为
 * FAILED(EXECUTION_FAILED)。超时与取消必须经由上下文中的 TurnDeadline 与
 * CancellationSignal 协作检查，不得自行创建时间预算。
 */
public interface SemanticTaskExecutor {
    SemanticTask.SourceDomain getSourceDomain();
    TaskExecutionResult execute(TaskExecutionContext context);
}
