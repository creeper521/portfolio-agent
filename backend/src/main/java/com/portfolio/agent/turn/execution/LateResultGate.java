package com.portfolio.agent.turn.execution;

import java.util.concurrent.atomic.AtomicBoolean;

final class LateResultGate {
    private final AtomicBoolean settled = new AtomicBoolean();
    boolean tryAccept() { return !settled.get(); }
    boolean settle() { return settled.compareAndSet(false, true); }
}
