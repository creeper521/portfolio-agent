package com.portfolio.agent.turn.lifecycle;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveTurnRegistry {
    private final ConcurrentHashMap<UUID, Runnable> active = new ConcurrentHashMap<>();
    void claimOwner(UUID requestId, Runnable cancelAction) {
        if (active.putIfAbsent(requestId, cancelAction) != null) {
            throw new IllegalStateException("turn is already active");
        }
    }
    boolean cancel(UUID requestId) {
        Runnable action = active.get(requestId);
        if (action == null) return false;
        action.run();
        return true;
    }
    void releaseOwner(UUID requestId, Runnable cancelAction) { active.remove(requestId, cancelAction); }
}
