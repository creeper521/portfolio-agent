package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.execution.CancellationSignal;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveTurnRegistry {
    private final ConcurrentHashMap<UUID, CancellationSignal> active = new ConcurrentHashMap<>();
    CancellationSignal register(UUID requestId) {
        CancellationSignal signal = new CancellationSignal();
        if (active.putIfAbsent(requestId, signal) != null) {
            throw new IllegalStateException("turn is already active");
        }
        return signal;
    }
    boolean cancel(UUID requestId) {
        CancellationSignal signal = active.get(requestId);
        return signal != null && signal.cancel();
    }
    void remove(UUID requestId, CancellationSignal signal) { active.remove(requestId, signal); }
}
