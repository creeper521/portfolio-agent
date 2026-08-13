package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;

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
    private final AnswerConstructionMode constructionMode;
    private final AnswerIntentSource intentSource;
    private final AnswerEvidenceState evidenceState;
    private final String noticeCode;
    private final List<ConversationTopic> coveredTopics;
    private final ConversationGuidanceStage guidanceStage;
    private final PortfolioRecommendationResponse portfolioRecommendation;
    private final boolean contextVersionUpdated;
    private final String questionPresetId;
    private final String contractVersion;
    private final String summary;
    private final AgentTurnResponse agentTurn;
    private final String responseKind;
    private final ConversationResponse conversation;

    public ConversationAnswerResponse(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlockResponse> blocks,
            List<ConversationSuggestedQuestionResponse> suggestedQuestions,
            boolean degraded,
            AnswerConstructionMode constructionMode,
            AnswerIntentSource intentSource,
            AnswerEvidenceState evidenceState,
            String noticeCode,
            List<ConversationTopic> coveredTopics,
            ConversationGuidanceStage guidanceStage,
            PortfolioRecommendationResponse portfolioRecommendation,
            boolean contextVersionUpdated,
            String questionPresetId,
            String contractVersion,
            String summary,
            AgentTurnResponse agentTurn) {
        this(turnId, contentVersion, intent, answerScope, resolution, title, blocks, suggestedQuestions,
                degraded, constructionMode, intentSource, evidenceState, noticeCode, coveredTopics,
                guidanceStage, portfolioRecommendation, contextVersionUpdated, questionPresetId,
                contractVersion, summary, agentTurn, "ANSWER", null);
    }

    public ConversationAnswerResponse(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlockResponse> blocks,
            List<ConversationSuggestedQuestionResponse> suggestedQuestions,
            boolean degraded,
            AnswerConstructionMode constructionMode,
            AnswerIntentSource intentSource,
            AnswerEvidenceState evidenceState,
            String noticeCode,
            List<ConversationTopic> coveredTopics,
            ConversationGuidanceStage guidanceStage,
            PortfolioRecommendationResponse portfolioRecommendation,
            boolean contextVersionUpdated,
            String questionPresetId,
            String contractVersion,
            String summary,
            AgentTurnResponse agentTurn,
            String responseKind,
            ConversationResponse conversation) {
        this.turnId = turnId;
        this.contentVersion = contentVersion;
        this.intent = intent;
        this.answerScope = answerScope;
        this.resolution = resolution;
        this.title = title;
        this.blocks = List.copyOf(blocks);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
        this.degraded = degraded;
        this.constructionMode = constructionMode;
        this.intentSource = intentSource;
        this.evidenceState = evidenceState;
        this.noticeCode = noticeCode;
        this.coveredTopics = List.copyOf(coveredTopics);
        this.guidanceStage = guidanceStage;
        this.portfolioRecommendation = portfolioRecommendation;
        this.contextVersionUpdated = contextVersionUpdated;
        this.questionPresetId = questionPresetId;
        this.contractVersion = contractVersion;
        this.summary = summary;
        this.agentTurn = agentTurn;
        this.responseKind = responseKind;
        this.conversation = conversation;
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
    public AnswerConstructionMode getConstructionMode() { return constructionMode; }
    public AnswerIntentSource getIntentSource() { return intentSource; }
    public AnswerEvidenceState getEvidenceState() { return evidenceState; }
    public String getNoticeCode() { return noticeCode; }
    public List<ConversationTopic> getCoveredTopics() { return coveredTopics; }
    public ConversationGuidanceStage getGuidanceStage() { return guidanceStage; }
    public boolean isContextVersionUpdated() { return contextVersionUpdated; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getQuestionPresetId() { return questionPresetId; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContractVersion() { return contractVersion; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSummary() { return summary; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public PortfolioRecommendationResponse getPortfolioRecommendation() {
        return portfolioRecommendation;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AgentTurnResponse getAgentTurn() { return agentTurn; }
    public String getResponseKind() { return responseKind; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ConversationResponse getConversation() { return conversation; }
}
