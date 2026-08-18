package com.portfolio.agent.turn.capability.general;

/** Dedicated model seam. It accepts no conversation, route, evidence, or rendered text. */
public interface GeneralKnowledgeModelPort {
    String generate(GeneralKnowledgeRequest request) throws GeneralKnowledgeUnavailableException;
}
