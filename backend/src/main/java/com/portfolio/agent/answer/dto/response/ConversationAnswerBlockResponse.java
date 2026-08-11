package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ConversationSourceScope;

import java.util.List;

public final class ConversationAnswerBlockResponse {

    private final ConversationSourceScope sourceScope;
    private final AnswerSectionType sectionType;
    private final String title;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this(sourceScope, null, null, content, claimIds, evidenceIds);
    }

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this.sourceScope = sourceScope;
        this.sectionType = sectionType;
        this.title = title;
        this.content = content;
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AnswerSectionType getSectionType() { return sectionType; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
