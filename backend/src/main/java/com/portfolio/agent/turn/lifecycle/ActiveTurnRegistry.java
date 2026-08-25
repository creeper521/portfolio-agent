package com.portfolio.agent.turn.lifecycle;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内活跃 Turn 注册表：requestId 到取消动作的单射映射。
 *
 * <p>同一 requestId 只允许一个所有者持有取消权，用于把外部取消请求路由到正在执行的
 * Turn 的 {@code CancellationSignal}。线程安全基于 {@link ConcurrentHashMap} 的原子
 * putIfAbsent/remove(key, value)；所有权必须与 State 层的 lease 生命周期一致，
 * 结算或失败路径必须在 finally 中释放，否则取消动作会指向已结束的执行。</p>
 */
final class ActiveTurnRegistry {
    private final ConcurrentHashMap<UUID, Runnable> active = new ConcurrentHashMap<>();

    /**
     * 登记一个 Turn 的取消动作。
     *
     * @throws IllegalStateException 该 requestId 已有活跃所有者（重复 Claim 属于编程错误）
     */
    void claimOwner(UUID requestId, Runnable cancelAction) {
        if (active.putIfAbsent(requestId, cancelAction) != null) {
            throw new IllegalStateException("turn is already active");
        }
    }

    /**
     * 触发指定 Turn 的取消动作（若有）。
     *
     * <p>动作由所有者线程注册，本方法可能在任意请求线程调用；只负责发出取消信号，
     * 不等待执行结束，也不移除注册表条目。</p>
     *
     * @return 是否存在活跃 Turn 并已触发其取消动作
     */
    boolean cancel(UUID requestId) {
        Runnable action = active.get(requestId);
        if (action == null) return false;
        action.run();
        return true;
    }

    /** 释放所有权；仅当条目仍是注册时的同一取消动作时才移除，防止误删新所有者。 */
    void releaseOwner(UUID requestId, Runnable cancelAction) { active.remove(requestId, cancelAction); }
}
