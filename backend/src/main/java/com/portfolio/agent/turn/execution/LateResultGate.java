package com.portfolio.agent.turn.execution;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批结果结算门：Engine 退出结果收集循环后关闭，拒绝其后才完成的结果。
 *
 * <p>取消或超时后仍在运行的任务可能稍后返回结果；settle 关门后 tryAccept
 * 恒为 false，迟到结果不再写入 outcomes，保证终态视图一经结算不再变化。
 * {@code tryAccept} 是非原子的尽力检查，正确性由 Engine 单线程串行的
 * 收集-结算顺序保证。
 */
final class LateResultGate {
    private final AtomicBoolean settled = new AtomicBoolean();
    boolean tryAccept() { return !settled.get(); }
    boolean settle() { return settled.compareAndSet(false, true); }
}
