package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;

import java.util.Objects;
import java.util.Optional;

/**
 * 检索降级策略（无状态策略对象）：依据失败分类决定是否发起一次降级检索。
 *
 * <p>仅两条降级路径：HYBRID 主检索遇 VECTOR_UNAVAILABLE 时降为同后端 KEYWORD；
 * 后端连接不可用或超时时切换到 invocation 预设的备用后端。其余失败一律不再重试，
 * 交由能力编排器以 fail-closed 方式终止，避免无限重试或跨快照混用。
 */
public final class RetrievalFallbackPolicy {
    /**
     * 计算失败后的降级检索请求。
     *
     * @param invocation 当前 Evidence 调用（提供主/备后端与策略）
     * @param failure    本次失败分类
     * @return 可降级时给出下一次请求；无降级路径时为 empty
     */
    public Optional<RetrievalRequest> fallbackFor(
            PortfolioEvidenceInvocation invocation, RetrievalAttemptFailure failure) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(failure, "failure");
        if (failure == RetrievalAttemptFailure.VECTOR_UNAVAILABLE
                && invocation.getPrimaryStrategy() == SearchStrategy.HYBRID) {
            return Optional.of(new RetrievalRequest(
                    invocation.getPrimaryBackend(), SearchStrategy.KEYWORD));
        }
        if ((failure == RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE
                || failure == RetrievalAttemptFailure.BACKEND_TIMEOUT)
                && invocation.getFallbackBackend() != null) {
            return Optional.of(new RetrievalRequest(
                    invocation.getFallbackBackend(), invocation.getFallbackStrategy()));
        }
        return Optional.empty();
    }
}
