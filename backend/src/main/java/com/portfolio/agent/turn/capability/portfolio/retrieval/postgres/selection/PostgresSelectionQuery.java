package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.util.List;

/**
 * 公开 PostgreSQL 候选选择查询端口：定义候选选择所需的只读查询面。
 *
 * <p>由 {@link JdbcPostgresSelectionQuery} 实现全部查询；{@link #findByIds} 的默认实现
 * 刻意抛出 UnsupportedOperationException，精确主体查询属于可选能力，未实现时由调用方降级。
 */
public interface PostgresSelectionQuery {

    /** 查询当前生效的公开内容发布。 */
    ActiveRelease activeRelease();

    /**
     * 全文检索候选。
     *
     * @param releaseId 内容发布标识，锁定检索快照
     * @param target    选择目标（主体范围与检索词）
     * @param limit     返回行数上限
     * @return 按相关性排序的候选行
     */
    List<PostgresSelectionRow> searchFts(
            String releaseId,
            SelectionTarget target,
            int limit);

    /**
     * 向量相似度检索候选。
     *
     * @param releaseId 内容发布标识
     * @param embedding 查询向量
     * @param target    选择目标
     * @param limit     返回行数上限
     * @return 按相似度排序的候选行
     */
    List<PostgresSelectionRow> searchVector(
            String releaseId,
            float[] embedding,
            SelectionTarget target,
            int limit);

    /**
     * 按主体标识精确查询候选（可选能力）。
     *
     * @throws UnsupportedOperationException 实现未提供精确查询时
     */
    default List<PostgresSelectionRow> findByIds(
            String releaseId,
            List<String> subjectIds,
            SelectionTarget target) {
        throw new UnsupportedOperationException("exact subject lookup is not implemented");
    }
}
