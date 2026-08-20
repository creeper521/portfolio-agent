package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConversationAnswerBlock {

    private final ConversationSourceScope sourceScope;
    private final AnswerSectionType sectionType;
    private final String title;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final List<PublicSourceReferenceValue> sourceReferences;

    @JsonCreator
    public ConversationAnswerBlock(
            @JsonProperty("sourceScope") ConversationSourceScope sourceScope,
            @JsonProperty("content") String content,
            @JsonProperty("claimIds") List<String> claimIds,
            @JsonProperty("evidenceIds") List<String> evidenceIds
    ) {
        this(sourceScope, null, null, content, claimIds, evidenceIds, List.of());
    }

    public ConversationAnswerBlock(
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds) {
        this(sourceScope, sectionType, title, content, claimIds, evidenceIds, List.of());
    }

    public ConversationAnswerBlock(
            ConversationSourceScope sourceScope,
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceValue> sourceReferences) {
        this.sourceScope = Objects.requireNonNull(sourceScope, "sourceScope");
        this.sectionType = sectionType;
        this.title = normalizeNullable(title);
        this.content = Objects.requireNonNull(content, "content");
        this.claimIds = stableDistinct(claimIds, "claimIds");
        this.evidenceIds = stableDistinct(evidenceIds, "evidenceIds");
        this.sourceReferences = List.copyOf(
                Objects.requireNonNull(sourceReferences, "sourceReferences"));
    }

    public ConversationSourceScope getSourceScope() { return sourceScope; }
    public AnswerSectionType getSectionType() { return sectionType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }

    private static List<String> stableDistinct(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        List<String> distinct = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !distinct.contains(value.trim())) {
                distinct.add(value.trim());
            }
        }
        return List.copyOf(distinct);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
