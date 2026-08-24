package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;

/** Dedicated model seam. It accepts no conversation, route, evidence, or rendered text. */
public interface GeneralKnowledgeModelPort {
    String generate(
            GeneralKnowledgeRequest request,
            ResolvedModelExecution modelExecution)
            throws GeneralKnowledgeUnavailableException;
}
