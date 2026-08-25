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

/**
 * 生产 Agent State 未配置（DISABLED 只读作品集模式）时的 fail-closed 适配器。
 *
 * <p>所有 State 操作一律抛出 IllegalStateException，使 Agent Turn 请求在生命
 * 周期内收敛为 STORE_UNAVAILABLE，而不是静默降级为无状态执行。</p>
 */
public final class UnavailableTurnExecutionStore implements AgentStateStore {
    /** 统一的不可用异常（无状态细节泄露）。 */
    private IllegalStateException unavailable() { return new IllegalStateException("agent state unavailable"); }
    @Override public ClaimResult claim(UUID id, String conversation, com.portfolio.agent.turn.lifecycle.RequestFingerprintSet fingerprints, SessionAccess access, Instant now, Duration lease, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public boolean complete(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, ConversationSessionStore.Session session, SessionAccess access, Instant completedAt, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public SettlementResult completeWithSession(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, ConversationSessionStore.Session session, SessionAccess access, Instant completedAt, com.portfolio.agent.turn.execution.TurnDeadline deadline, com.portfolio.agent.turn.continuation.DiscussionStateMutation discussion, com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarification) { throw unavailable(); }
    @Override public SettlementResult completeWithSession(UUID id, byte[] fingerprint, PublicAgentTurn snapshot, List<ContinuationContext> contexts, List<ClarificationStore.Record> challenges, ConversationSessionStore.Session session, SessionAccess access, Instant completedAt, com.portfolio.agent.turn.execution.TurnDeadline deadline, com.portfolio.agent.turn.continuation.DiscussionStateMutation discussion, com.portfolio.agent.turn.continuation.ClarificationSettlementMutation clarification, com.portfolio.agent.turn.continuation.ConversationSemanticState semanticState) { throw unavailable(); }
    @Override public boolean cancel(UUID id, String conversation, Instant cancelledAt) { throw unavailable(); }
    @Override public Optional<TurnExecutionRecord> find(UUID id) { throw unavailable(); }
    @Override public boolean clearConversation(String conversationId, byte[] tokenHash, Instant clearedAt) { throw unavailable(); }
    @Override public Optional<ContinuationContext> findContext(String conversationId, String contextHandle, Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
    @Override public ClarificationStore.ReserveResult reserveClarification(String id, String conversation, byte[] tokenHash, String release, ClarificationStore.ClarificationAnswer answer, UUID requestId, Instant reservationExpiresAt, Instant now, com.portfolio.agent.turn.execution.TurnDeadline deadline) { throw unavailable(); }
}
