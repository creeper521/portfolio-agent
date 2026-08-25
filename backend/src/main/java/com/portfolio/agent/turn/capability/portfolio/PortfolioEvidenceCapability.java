package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.evidence.EvidencePromotionValidator;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioRetrieverPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptFailure;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作品集 Evidence 能力的执行编排器：一次主检索、至多一次分类降级、一次证据晋级。
 *
 * <p>在 Turn 的 Execution 阶段被 {@link PortfolioTaskExecutor} 调用：先按 Invocation
 * 的主 backend/strategy 发起检索；仅当失败被 {@link RetrievalFallbackPolicy} 判定
 * 可降级且未超过截止时间时，才再发起至多一次 fallback 检索；成功的候选集必须经
 * {@link EvidencePromotionValidator} 晋级为已验证 Evidence。任何一步失败都以
 * {@link PortfolioCapabilityException} 终止，由上层映射为 Task 终止，从不放行
 * 未验证内容（fail-closed）。
 */
public final class PortfolioEvidenceCapability {
    private final Map<CorpusBackend, PortfolioRetrieverPort> retrievers;
    private final RetrievalFallbackPolicy fallbackPolicy;
    private final EvidencePromotionValidator promotionValidator;

    public PortfolioEvidenceCapability(
            Map<CorpusBackend, PortfolioRetrieverPort> retrievers,
            RetrievalFallbackPolicy fallbackPolicy,
            EvidencePromotionValidator promotionValidator) {
        EnumMap<CorpusBackend, PortfolioRetrieverPort> copy = new EnumMap<>(CorpusBackend.class);
        copy.putAll(Objects.requireNonNull(retrievers, "retrievers"));
        this.retrievers = Map.copyOf(copy);
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        this.promotionValidator = Objects.requireNonNull(promotionValidator, "promotionValidator");
    }

    /**
     * 执行一次 Evidence 检索，返回晋级后的已验证 Evidence 捆绑包。
     *
     * <p>流程：检查截止时间 → 主检索 → 成功即晋级返回；失败时询问降级策略，
     * 仅在允许降级且未超时时执行一次 fallback 检索并晋级。
     *
     * @param invocation 已通过不变量校验的检索调用参数
     * @param deadline   本次 Turn 的截止时间，进入方法与降级前各检查一次
     * @return 晋级成功的 {@link ValidatedEvidenceBundle}
     * @throws PortfolioCapabilityException 截止时间已过期、backend 未配置、
     *         失败不允许降级，或主检索与 fallback 检索均失败时抛出，
     *         异常携带分类后的 {@link RetrievalAttemptFailure}
     */
    public ValidatedEvidenceBundle execute(
            PortfolioEvidenceInvocation invocation, TurnDeadline deadline) {
        if (deadline.isExpired()) throw new PortfolioCapabilityException(
                RetrievalAttemptFailure.CANCELLED);
        RetrievalRequest primary = new RetrievalRequest(
                invocation.getPrimaryBackend(), invocation.getPrimaryStrategy());
        RetrievalAttemptResult first = attempt(invocation, primary, deadline);
        if (first.isSuccessful()) return promote(first, invocation);
        RetrievalAttemptFailure failure = first.getFailure().orElseThrow();
        java.util.Optional<RetrievalRequest> fallback = fallbackPolicy.fallbackFor(invocation, failure);
        if (fallback.isEmpty() || deadline.isExpired()) {
            throw new PortfolioCapabilityException(failure);
        }
        RetrievalAttemptResult second = attempt(invocation, fallback.orElseThrow(), deadline);
        if (!second.isSuccessful()) {
            throw new PortfolioCapabilityException(second.getFailure().orElseThrow());
        }
        return promote(second, invocation);
    }

    /** 单次检索尝试：backend 未注册时归类为连接不可用，而非抛出异常。 */
    private RetrievalAttemptResult attempt(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        PortfolioRetrieverPort retriever = retrievers.get(request.getBackend());
        if (retriever == null) {
            return RetrievalAttemptResult.failure(
                    RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE);
        }
        return Objects.requireNonNull(
                retriever.retrieve(invocation, request, deadline), "retrieval result");
    }

    /** 将成功尝试的候选集按调用的 contentReleaseId 晋级为已验证 Evidence。 */
    private ValidatedEvidenceBundle promote(
            RetrievalAttemptResult result, PortfolioEvidenceInvocation invocation) {
        return promotionValidator.promote(
                result.getCandidateSet().orElseThrow(), invocation.getContentReleaseId());
    }

    /** 能力执行失败的终止异常，携带分类后的失败原因，供上层映射为 Task 终止理由。 */
    public static final class PortfolioCapabilityException extends RuntimeException {
        private final RetrievalAttemptFailure failure;
        public PortfolioCapabilityException(RetrievalAttemptFailure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }
        public RetrievalAttemptFailure getFailure() { return failure; }
    }
}
