package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;

/**
 * Dedicated model seam. It accepts no conversation, route, evidence, or rendered text.
 *
 * <p>通用能力的专用模型接缝：端口只接收 {@link GeneralKnowledgeRequest} 与冻结的
 * {@link ResolvedModelExecution} 快照；不进入会话上下文、路由、Evidence 或任何
 * 已渲染文本，隔离模型层与 Turn 其余状态。
 */
public interface GeneralKnowledgeModelPort {
    StructurallyValidatedOutput generate(
            GeneralKnowledgeRequest request,
            ResolvedModelExecution modelExecution)
            throws GeneralKnowledgeUnavailableException;
}
