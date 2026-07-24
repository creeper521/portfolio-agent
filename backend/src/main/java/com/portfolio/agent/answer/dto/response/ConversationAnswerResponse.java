package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;

import java.util.List;

public final class ConversationAnswerResponse {

    private final String turnId;
    private final String contentVersion;
    private final ConversationIntent intent;
    private final ConversationAnswerScope answerScope;
    private final AnswerResolution resolution;
    private final String title;
    private final List<ConversationAnswerBlockResponse> blocks;
    private final List<ConversationSuggestedQuestionResponse> suggestedQuestions;
    private final boolean degraded;

    public ConversationAnswerResponse(ConversationAnswerResult result) {
        this.turnId = result.getTurnId();
        this.contentVersion = result.getContentVersion();
        this.intent = result.getIntent();
        this.answerScope = result.getAnswerScope();
        this.resolution = result.getResolution();
        this.title = result.getTitle();
        this.blocks = result.getBlocks().stream()
                .map(ConversationAnswerBlockResponse::from)
                .toList();
        this.suggestedQuestions = result.getSuggestedQuestions().stream()
                .map(ConversationSuggestedQuestionResponse::from)
                .toList();
        this.degraded = result.isDegraded();
    }

    public String getTurnId() { return turnId; }
    public String getContentVersion() { return contentVersion; }
    public ConversationIntent getIntent() { return intent; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public AnswerResolution getResolution() { return resolution; }
    public String getTitle() { return title; }
    public List<ConversationAnswerBlockResponse> getBlocks() { return blocks; }
    public List<ConversationSuggestedQuestionResponse> getSuggestedQuestions() {
        return suggestedQuestions;
    }
    public boolean isDegraded() { return degraded; }
}
