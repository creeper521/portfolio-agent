package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;

import java.util.List;

/**
 * 事实段落查询端口：从公开 PostgreSQL 投影中读取指定主体的事实段落。
 *
 * <p>默认实现是内存过滤版的相关段落筛选：先取全部段落，再按文本相关性
 * （{@link PortfolioTextRelevance}）与偏好 claim 类别过滤并截断，供降级路径使用。
 */
@FunctionalInterface
public interface PostgresFactPassageQuery {

    /**
     * 读取指定发布下若干主体的全部事实段落。
     *
     * @param releaseId  内容发布标识，用于锁定公开快照
     * @param subjectIds 主体标识列表（项目或案例）
     * @return 命中的段落列表，顺序由实现决定
     */
    List<PostgresKnowledgePassageRow> findPassages(String releaseId, List<String> subjectIds);

    /**
     * 筛选与查询文本相关的事实段落：文本匹配 + 偏好 claim 类别过滤，再按 limit 截断。
     *
     * @param releaseId               内容发布标识
     * @param subjectIds              主体标识列表
     * @param query                   访问者查询文本
     * @param preferredClaimCategories 偏好的 claim 类别；为空表示不按类别过滤
     * @param limit                   返回段落数上限
     * @return 过滤并截断后的相关段落列表
     */
    default List<PostgresKnowledgePassageRow> findRelevantPassages(
            String releaseId,
            List<String> subjectIds,
            String query,
            List<AnswerClaimCategory> preferredClaimCategories,
            int limit
    ) {
        return findPassages(releaseId, subjectIds).stream()
                .filter(passage -> PortfolioTextRelevance.matches(
                        query, passage.getContent()))
                .filter(passage -> preferredClaimCategories.isEmpty()
                        || preferredClaimCategories.contains(passage.getClaimCategory()))
                .limit(limit)
                .toList();
    }
}
