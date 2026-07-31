package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.GenerationMode;

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
    private final GenerationMode generationMode;
    private final AnswerSource answerSource;
    private final String noticeCode;
    private final List<ConversationTopic> coveredTopics;
    private final ConversationGuidanceStage guidanceStage;

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
        this.generationMode = result.getGenerationMode();
        this.answerSource = result.getAnswerSource();
        this.noticeCode = result.getNoticeCode();
        this.coveredTopics = result.getProgress().getCoveredTopics();
        this.guidanceStage = result.getProgress().getStage();
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
    public GenerationMode getGenerationMode() { return generationMode; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public String getNoticeCode() { return noticeCode; }
    public List<ConversationTopic> getCoveredTopics() { return coveredTopics; }
    public ConversationGuidanceStage getGuidanceStage() { return guidanceStage; }
}
