package com.portfolio.agent.answer.domain;

import java.util.List;

public final class PortfolioGroundingContext {

    private final ConversationSubjectOption subject;
    private final List<AnswerClaimProjection> claims;
    private final List<AnswerEvidence> evidence;
    private final List<AnswerRetrievalChunk> chunks;

    public PortfolioGroundingContext(
            ConversationSubjectOption subject,
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            List<AnswerRetrievalChunk> chunks
    ) {
        this.subject = subject;
        this.claims = List.copyOf(claims);
        this.evidence = List.copyOf(evidence);
        this.chunks = List.copyOf(chunks);
    }

    public static PortfolioGroundingContext empty() {
        return new PortfolioGroundingContext(null, List.of(), List.of(), List.of());
    }

    public ConversationSubjectOption getSubject() { return subject; }
    public List<AnswerClaimProjection> getClaims() { return claims; }
    public List<AnswerEvidence> getEvidence() { return evidence; }
    public List<AnswerRetrievalChunk> getChunks() { return chunks; }
}
