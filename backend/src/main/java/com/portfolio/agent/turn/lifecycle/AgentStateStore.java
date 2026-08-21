package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.portfolio.agent.turn.execution.TurnDeadline;

/** State read surface paired with the TurnExecutionStore atomic settlement authority. */
public interface AgentStateStore extends TurnExecutionStore {
    Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now,
            TurnDeadline deadline);
    ClarificationStore.ReserveResult reserveClarification(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId,
            ClarificationStore.ClarificationAnswer answer,
            UUID requestId, Instant reservationExpiresAt, Instant now,
            TurnDeadline deadline);
}
