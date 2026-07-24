package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ConversationAnswerBlock {

    private final ConversationSourceScope sourceScope;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    public ConversationAnswerBlock(
            ConversationSourceScope sourceScope,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this.sourceScope = Objects.requireNonNull(sourceScope, "sourceScope");
        this.content = Objects.requireNonNull(content, "content");
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
