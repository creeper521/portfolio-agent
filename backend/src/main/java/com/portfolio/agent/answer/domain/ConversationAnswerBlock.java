package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class ConversationAnswerBlock {

    private final ConversationSourceScope sourceScope;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    @JsonCreator
    public ConversationAnswerBlock(
            @JsonProperty("sourceScope") ConversationSourceScope sourceScope,
            @JsonProperty("content") String content,
            @JsonProperty("claimIds") List<String> claimIds,
            @JsonProperty("evidenceIds") List<String> evidenceIds
    ) {
        this.sourceScope = Objects.requireNonNull(sourceScope, "sourceScope");
        this.content = Objects.requireNonNull(content, "content");
        this.claimIds = claimIds == null ? List.of() : List.copyOf(claimIds);
        this.evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
