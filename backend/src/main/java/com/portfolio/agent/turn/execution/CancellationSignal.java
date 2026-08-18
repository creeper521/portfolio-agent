package com.portfolio.agent.turn.execution;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationSignal {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public boolean cancel() { return cancelled.compareAndSet(false, true); }
    public boolean isCancelled() { return cancelled.get(); }
}
