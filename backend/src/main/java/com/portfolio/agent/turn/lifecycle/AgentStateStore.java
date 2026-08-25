package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.portfolio.agent.turn.execution.TurnDeadline;

/**
 * Agent State 的读取扩展面，与父接口 {@link TurnExecutionStore} 的原子结算权威配套。
 *
 * <p>在 Claim/Complete/Cancel 之外补充两类有租期与截止时间的读操作：按 ContextHandle
 * 解密恢复 ContinuationContext，以及澄清 challenge 的预留（reserve）。所有实现都必须
 * 在 Turn deadline 内完成，且只允许触碰已批准的加密短生命周期 typed state。</p>
 */
public interface AgentStateStore extends TurnExecutionStore {
    /**
     * 按 conversationId + contextHandle 查找未过期的 ContinuationContext。
     *
     * @param deadline State 操作必须在此之前完成
     * @return 未过期且可解密的上下文；不存在或已过期返回 empty
     */
    Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now,
            TurnDeadline deadline);

    /**
     * 为一次 ResolveClarification 请求原子预留 challenge。
     *
     * <p>预留成功后其他请求在预留过期前只能得到 IN_PROGRESS；预留与后续结算
     * （complete 时消费）共同保证同一 challenge 不会被并发消费两次。</p>
     *
     * @param resumeTokenHash 当前会话凭证哈希，用于绑定预留归属
     * @param currentContentReleaseId 当前公开内容版本，不匹配时预留被拒绝
     * @param reservationExpiresAt 预留过期时间（不得超过 challenge 本身的过期时间）
     */
    ClarificationStore.ReserveResult reserveClarification(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId,
            ClarificationStore.ClarificationAnswer answer,
            UUID requestId, Instant reservationExpiresAt, Instant now,
            TurnDeadline deadline);
}
