package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;

/**
 * 候选检索端口：按选择目标从公开 PostgreSQL 投影中拉取一组候选。
 *
 * <p>由 {@link PostgresHybridCandidateRetriever} 实现；实现必须只读取公开投影（隐私边界），
 * 并在向量检索不可用时按降级策略返回 FTS_ONLY 结果而非直接失败（fail-closed 之外的显式降级）。
 */
@FunctionalInterface
public interface CandidateRetrievalPort {

    /**
     * 检索与目标主体相关的候选集。
     *
     * @param target 选择目标（主体范围、检索词与限定条件）
     * @param limit  候选数量上限
     * @return 检索结果，含状态与降级信息，不因可恢复的后端故障抛出异常
     */
    CandidateRetrievalResult retrieve(SelectionTarget target, int limit);
}
