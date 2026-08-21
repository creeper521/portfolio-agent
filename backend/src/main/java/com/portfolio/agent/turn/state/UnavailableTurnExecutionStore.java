package com.portfolio.agent.turn.state;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fail-closed adapter used when production Agent State is not configured. */
public final class UnavailableTurnExecutionStore implements AgentStateStore {
    private IllegalStateException unavailable() { return new IllegalStateException("agent state unavailable"); }
    @Override public ClaimResult claim(UUID id, String conversation, com.portfolio.agent.turn.lifecycle.RequestFingerprintSet fingerprints, SessionAccess access, Instant now, Duration lease, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public boolean complete(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, ConversationSessionStore.Session session, SessionAccess access, Instant completedAt, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public SettlementResult completeWithSession(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, ConversationSessionStore.Session session, SessionAccess access, Instant completedAt, com.portfolio.agent.turn.execution.TurnDeadline deadline, com.portfolio.agent.turn.continuation.DiscussionStateMutation discussion, com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarification) { throw unavailable(); }
    @Override public boolean cancel(UUID id, String conversation, Instant cancelledAt) { throw unavailable(); }
    @Override public Optional<TurnExecutionRecord> find(UUID id) { throw unavailable(); }
    @Override public boolean clearConversation(String conversationId, byte[] tokenHash, Instant clearedAt) { throw unavailable(); }
    @Override public Optional<ContinuationContext> findContext(String conversationId, String contextHandle, Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public ClarificationStore.ReserveResult reserveClarification(String id, String conversation, byte[] tokenHash, String release, ClarificationStore.ClarificationAnswer answer, UUID requestId, Instant reservationExpiresAt, Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
}
