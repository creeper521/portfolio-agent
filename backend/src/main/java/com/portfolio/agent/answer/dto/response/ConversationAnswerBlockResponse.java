package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.List;

public final class ConversationAnswerBlockResponse {

    private final ConversationSourceScope sourceScope;
    private final AnswerSectionType sectionType;
    private final String title;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final List<PublicSourceReferenceResponse> sourceReferences;
    private final String blockId;
    private final TaskSourceDomain sourceDomain;
    private final AnswerBlockSupportResponse support;

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
        this.blockId = null;
        this.sourceDomain = null;
        this.support = null;
    }

    public ConversationAnswerBlockResponse(
            String blockId,
            TaskSourceDomain sourceDomain,
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceResponse> sourceReferences,
            AnswerBlockSupportResponse support) {
        this.sourceScope = sourceScope;
        this.sectionType = sectionType;
        this.title = title;
        this.content = content;
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.sourceReferences = List.copyOf(sourceReferences);
        this.blockId = requireText(blockId, "blockId");
        this.sourceDomain = java.util.Objects.requireNonNull(sourceDomain, "sourceDomain");
        this.support = java.util.Objects.requireNonNull(support, "support");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ConversationSourceScope getSourceScope() { return sourceScope; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AnswerSectionType getSectionType() { return sectionType; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTitle() { return title; }
    public String getContent() { return content; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getClaimIds() { return claimIds; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getEvidenceIds() { return evidenceIds; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<PublicSourceReferenceResponse> getSourceReferences() { return sourceReferences; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getBlockId() { return blockId; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TaskSourceDomain getSourceDomain() { return sourceDomain; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AnswerBlockSupportResponse getSupport() { return support; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
