package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.execution.TurnDeadline;

/**
 * 作品集检索端口：能力编排器与具体检索后端（PostgreSQL 投影或打包快照）之间的边界。
 *
 * <p>实现负责把一次 {@link RetrievalRequest} 转换为候选集，并遵守 TurnDeadline；
 * 结果以 {@link RetrievalAttemptResult} 显式表达成功或失败，不向上抛出后端异常。
 */
public interface PortfolioRetrieverPort {
    /**
     * 执行一次作品集检索。
     *
     * @param invocation 当前 Evidence 调用上下文（含获准的主体范围）
     * @param request    检索请求（策略、关键词、限额）
     * @param deadline   本 Turn 的截止时间，超时必须以失败结果返回
     * @return 检索尝试结果；成功携带候选集，失败携带原因分类
     */
    RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline);
}
