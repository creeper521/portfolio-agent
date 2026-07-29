package com.portfolio.agent.common.observability;

import java.util.concurrent.atomic.AtomicLong;

public final class DroppedDiagnosticCounter {

    private final AtomicLong droppedCount = new AtomicLong();

    public long count() {
        return droppedCount.get();
    }

    void increment() {
        droppedCount.incrementAndGet();
    }
}
