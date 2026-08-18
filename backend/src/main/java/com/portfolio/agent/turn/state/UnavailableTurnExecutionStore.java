package com.portfolio.agent.turn.state;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fail-closed adapter used when production Agent State is not configured. */
public final class UnavailableTurnExecutionStore implements TurnExecutionStore {
    private IllegalStateException unavailable() { return new IllegalStateException("agent state unavailable"); }
    @Override public ClaimResult claim(UUID id, String conversation, byte[] fingerprint, Instant now, Duration lease) { throw unavailable(); }
    @Override public boolean complete(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, Instant completedAt) { throw unavailable(); }
    @Override public boolean cancel(UUID id, String conversation, Instant cancelledAt) { throw unavailable(); }
    @Override public Optional<TurnExecutionRecord> find(UUID id) { throw unavailable(); }
    @Override public void clearConversation(String conversationId) { throw unavailable(); }
}
