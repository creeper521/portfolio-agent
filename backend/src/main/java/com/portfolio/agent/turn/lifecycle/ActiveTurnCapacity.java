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

    public Lease acquire() {
        if (!permits.tryAcquire()) {
            throw new AgentAdmissionRejectedException(
                    AgentAdmissionRejectedException.RejectionReason.GLOBAL_ACTIVE_TURN_LIMIT,
                    1);
        }
        return new Lease(permits);
    }

    public static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Semaphore permits;

        private Lease(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
