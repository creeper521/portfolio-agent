package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
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

    public ConversationAnswerResponse(ConversationAnswerResult result) {
        this.turnId = result.getTurnId();
        this.contentVersion = result.getContentVersion();
        this.intent = result.getIntent();
        this.answerScope = publicScope(result.getAnswerScope());
        this.resolution = publicResolution(result.getResolution());
        this.title = result.getTitle();
        this.blocks = result.getBlocks().stream()
                .map(ConversationAnswerBlockResponse::from)
                .toList();
        this.suggestedQuestions = result.getSuggestedQuestions().stream()
                .map(ConversationSuggestedQuestionResponse::from)
                .toList();
        this.degraded = result.isDegraded();
        this.constructionMode = result.getConstructionMode();
        this.intentSource = result.getIntentSource();
        this.evidenceState = result.getEvidenceState();
        this.noticeCode = result.getNoticeCode();
        this.coveredTopics = result.getProgress().getCoveredTopics();
        this.guidanceStage = result.getProgress().getStage();
        this.portfolioRecommendation = result.getPortfolioRecommendation() == null
                ? null
                : PortfolioRecommendationResponse.from(result.getPortfolioRecommendation());
        this.contextVersionUpdated = result.isContextVersionUpdated();
        this.questionPresetId = result.getQuestionPresetId();
        this.contractVersion = result.getContractVersion();
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
    public PortfolioRecommendationResponse getPortfolioRecommendation() {
        return portfolioRecommendation;
    }

    private static AnswerResolution publicResolution(AnswerResolution resolution) {
        return resolution == AnswerResolution.BOUNDARY
                ? AnswerResolution.NEEDS_CLARIFICATION
                : resolution;
    }

    private static ConversationAnswerScope publicScope(ConversationAnswerScope scope) {
        return switch (scope) {
            case CONVERSATION -> ConversationAnswerScope.GLOBAL;
            case HYBRID -> ConversationAnswerScope.MIXED;
            case GENERAL, PORTFOLIO, GLOBAL, MIXED -> scope;
        };
    }

}
