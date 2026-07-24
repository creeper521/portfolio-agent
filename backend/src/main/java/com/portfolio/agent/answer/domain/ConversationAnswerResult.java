package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ConversationAnswerResult {

    private final String turnId;
    private final String contentVersion;
    private final ConversationIntent intent;
    private final ConversationAnswerScope answerScope;
    private final AnswerResolution resolution;
    private final String title;
    private final List<ConversationAnswerBlock> blocks;
    private final List<ConversationSuggestedQuestion> suggestedQuestions;
    private final boolean degraded;

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded
    ) {
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.answerScope = Objects.requireNonNull(answerScope, "answerScope");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.title = Objects.requireNonNull(title, "title");
        this.blocks = List.copyOf(blocks);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
        this.degraded = degraded;
    }

    public String getTurnId() { return turnId; }
    public String getContentVersion() { return contentVersion; }
    public ConversationIntent getIntent() { return intent; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public AnswerResolution getResolution() { return resolution; }
    public String getTitle() { return title; }
    public List<ConversationAnswerBlock> getBlocks() { return blocks; }
    public List<ConversationSuggestedQuestion> getSuggestedQuestions() {
        return suggestedQuestions;
    }
    public boolean isDegraded() { return degraded; }
}
