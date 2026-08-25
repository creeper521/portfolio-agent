package com.portfolio.agent.turn.execution;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turn 级协作式取消信号：一次触发、全程可查询。
 *
 * <p>由 Turn 生命周期持有并随 {@link TaskExecutionContext} 传入每个任务；
 * 任一持有者触发 {@link #cancel()} 后，Engine 与各 Capability 在既有检查点
 * 停止推进并收敛为终止态结果，不做强制中断。基于 {@link AtomicBoolean}，
 * 线程安全且幂等：仅首次触发返回 true。
 */
public final class CancellationSignal {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public boolean cancel() { return cancelled.compareAndSet(false, true); }
    public boolean isCancelled() { return cancelled.get(); }
}
