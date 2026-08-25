package com.portfolio.agent.common.observability;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 丢弃诊断计数器：统计因发布失败或过滤被丢弃的 diagnostic 事件数量，
 * 供运维侧在可观测性端点暴露，判断日志管道是否出现静默丢失。
 *
 * <p>基于 {@link AtomicLong} 计数，实例可安全并发使用；只记录数量，
 * 不保留被丢弃事件的内容，避免二次泄漏。</p>
 */
public final class DroppedDiagnosticCounter {

    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * 返回自进程启动以来累计丢弃的事件数。
     */
    public long count() {
        return droppedCount.get();
    }

    /**
     * 丢弃计数加一（包内可见，由诊断发布失败路径调用）。
     */
    void increment() {
        droppedCount.incrementAndGet();
    }
}
