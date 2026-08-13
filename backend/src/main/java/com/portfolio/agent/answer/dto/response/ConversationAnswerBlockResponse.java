package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private final List<PublicSourceReferenceResponse> sourceReferences;

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this(sourceScope, null, null, content, claimIds, evidenceIds, List.of());
    }

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceResponse> sourceReferences
    ) {
        this(sourceScope, null, null, content, claimIds, evidenceIds, sourceReferences);
    }

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this(sourceScope, sectionType, title, content, claimIds, evidenceIds, List.of());
    }

    public ConversationAnswerBlockResponse(
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceResponse> sourceReferences
    ) {
        this.sourceScope = sourceScope;
        this.sectionType = sectionType;
        this.title = title;
        this.content = content;
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.sourceReferences = List.copyOf(sourceReferences);
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AnswerSectionType getSectionType() { return sectionType; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTitle() { return title; }
    public String getContent() { return content; }
    @JsonIgnore
    public List<String> getClaimIds() { return claimIds; }
    @JsonIgnore
    public List<String> getEvidenceIds() { return evidenceIds; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<PublicSourceReferenceResponse> getSourceReferences() { return sourceReferences; }
}
