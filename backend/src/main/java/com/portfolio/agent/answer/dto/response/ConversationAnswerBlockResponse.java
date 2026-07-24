package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationSourceScope;

import java.util.List;

public final class ConversationAnswerBlockResponse {

    private final ConversationSourceScope sourceScope;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this.sourceScope = sourceScope;
        this.content = content;
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
    }

    public static ConversationAnswerBlockResponse from(ConversationAnswerBlock block) {
        return new ConversationAnswerBlockResponse(
                block.getSourceScope(),
                block.getContent(),
                block.getClaimIds(),
                block.getEvidenceIds());
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
