package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;

/**
 * 公开 PostgreSQL 知识检索端口：把一次 Evidence 检索请求转换为候选集与知识段落。
 *
 * <p>由 {@link JdbcPostgresKnowledgeQuery} 以只读 SQL 实现，仅访问公开投影表；
 * 失败不在接口层吞掉，由实现包装为可分类的异常交由上层降级决策。
 */
@FunctionalInterface
public interface PostgresKnowledgeQuery {

    /**
     * 执行一次知识检索。
     *
     * @param invocation 当前 Evidence 调用上下文（含获准主体范围与内容发布版本）
     * @param request    检索请求（后端与策略）
     * @return 候选集与命中知识段落的组合结果
     */
    PostgresKnowledgeQueryResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request);
}
