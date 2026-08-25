package com.portfolio.agent.turn.lifecycle;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单实例 Active Turn 的硬容量边界。 */
public final class ActiveTurnCapacity {
    private final Semaphore permits;

    public ActiveTurnCapacity(int maxActiveTurns) {
        if (maxActiveTurns < 1) {
            throw new IllegalArgumentException("maxActiveTurns must be positive");
        }
        this.permits = new Semaphore(maxActiveTurns);
    }

    /**
     * 获取一个全局 Active Turn 许可。
     *
     * <p>非阻塞：单实例许可耗尽时立即抛出
     * {@link AgentAdmissionRejectedException}（GLOBAL_ACTIVE_TURN_LIMIT，建议 1 秒后重试），
     * 而不是排队等待。</p>
     */
    public Lease acquire() {
        if (!permits.tryAcquire()) {
            throw new AgentAdmissionRejectedException(
                    AgentAdmissionRejectedException.RejectionReason.GLOBAL_ACTIVE_TURN_LIMIT,
                    1);
        }
        return new Lease(permits);
    }

    /** 一次性 Active Turn 许可；必须通过 close() 归还，可用 try-with-resources 管理。 */
    public static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Semaphore permits;

        private Lease(Semaphore permits) {
            this.permits = permits;
        }

        /** 幂等归还许可：重复 close 只释放一次，避免并发归还放大容量。 */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
