package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ModelDraftSection {

    private final AnswerSectionType type;
    private final String title;
    private final String content;
    private final List<String> evidenceIds;
    private final List<String> claimIds;

    @JsonCreator
    public ModelDraftSection(
            @JsonProperty("type") AnswerSectionType type,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("evidenceIds") List<String> evidenceIds,
            @JsonProperty("claimIds") List<String> claimIds
    ) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        this.claimIds = claimIds == null ? List.of() : List.copyOf(claimIds);
    }

    public AnswerSectionType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getClaimIds() { return claimIds; }
}
