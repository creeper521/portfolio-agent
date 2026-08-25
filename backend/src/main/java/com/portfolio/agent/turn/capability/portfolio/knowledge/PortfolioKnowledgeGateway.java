package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.Optional;

/**
 * 回答知识的获取网关：向 Agent 运行时暴露只读的公开回答知识内容。
 *
 * <p>标准实现为本地快照适配器 {@link LocalPortfolioKnowledgeAdapter}；
 * 实现必须保证返回内容仅来自已审定公开快照（隐私边界），不得引入私有库数据。
 */
public interface PortfolioKnowledgeGateway {

    /** 返回当前运行时回答知识内容，每次调用反映当前生效的快照。 */
    RuntimeAnswerContent getContent();

    /**
     * 按项目 slug 查找项目知识。
     *
     * @param projectSlug 项目 slug
     * @return 匹配的项目知识；不存在时为 empty
     */
    default Optional<AnswerKnowledge> findBySlug(String projectSlug) {
        return getContent().getProjects().stream()
                .filter(project -> project.getSlug().equals(projectSlug))
                .findFirst();
    }
}
