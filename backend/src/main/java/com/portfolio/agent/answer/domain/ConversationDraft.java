package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ConversationDraft {

    private final String title;
    private final AnswerResolution resolution;
    private final List<ConversationAnswerBlock> blocks;

    @JsonCreator
    public ConversationDraft(
            @JsonProperty("title") String title,
            @JsonProperty("resolution") AnswerResolution resolution,
            @JsonProperty("blocks") List<ConversationAnswerBlock> blocks
    ) {
        this.title = title;
        this.resolution = resolution;
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public String getTitle() { return title; }
    public AnswerResolution getResolution() { return resolution; }
    public List<ConversationAnswerBlock> getBlocks() { return blocks; }
}
