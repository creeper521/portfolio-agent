package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ModelAnswerDraft {

    private final String title;
    private final String summary;
    private final List<ModelDraftSection> sections;

    @JsonCreator
    public ModelAnswerDraft(
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("sections") List<ModelDraftSection> sections
    ) {
        this.title = title;
        this.summary = summary;
        this.sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public List<ModelDraftSection> getSections() { return sections; }
}
