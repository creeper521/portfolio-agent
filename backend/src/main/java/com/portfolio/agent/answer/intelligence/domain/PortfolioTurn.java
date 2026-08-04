package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationWindow;

import java.util.List;

public final class PortfolioTurn {

    private final String turnId;
    private final String question;
    private final String questionPresetId;
    private final String contractVersion;
    private final ConversationWindow window;
    private final String projectSlug;
    private final String caseSlug;
    private final PortfolioRecommendationContext recommendationContext;
    private final PortfolioReferenceContext referenceContext;
    private final String audienceRole;
    private final String source;

    private PortfolioTurn(Builder builder) {
        this.turnId = requireText(builder.turnId, "turnId");
        this.question = requireText(builder.question, "question");
        this.questionPresetId = normalizeText(builder.questionPresetId);
        this.contractVersion = normalizeText(builder.contractVersion);
        this.window = builder.window == null
                ? new ConversationWindow(null, List.<ConversationMessage>of(), 0)
                : builder.window;
        this.projectSlug = normalizeText(builder.projectSlug);
        this.caseSlug = normalizeText(builder.caseSlug);
        if (projectSlug != null && caseSlug != null) {
            throw new IllegalArgumentException("projectSlug and caseSlug cannot both be set");
        }
        this.recommendationContext = builder.recommendationContext;
        this.referenceContext = builder.referenceContext;
        this.audienceRole = normalizeText(builder.audienceRole);
        this.source = normalizeText(builder.source);
    }

    public static Builder builder(String turnId, String question) {
        return new Builder(turnId, question);
    }

    public String getTurnId() { return turnId; }
    public String getQuestion() { return question; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getContractVersion() { return contractVersion; }
    public ConversationWindow getWindow() { return window; }
    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public PortfolioRecommendationContext getRecommendationContext() {
        return recommendationContext;
    }
    public PortfolioReferenceContext getReferenceContext() { return referenceContext; }
    public String getAudienceRole() { return audienceRole; }
    public String getSource() { return source; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {

        private final String turnId;
        private final String question;
        private String questionPresetId;
        private String contractVersion;
        private ConversationWindow window;
        private String projectSlug;
        private String caseSlug;
        private PortfolioRecommendationContext recommendationContext;
        private PortfolioReferenceContext referenceContext;
        private String audienceRole;
        private String source;

        private Builder(String turnId, String question) {
            this.turnId = turnId;
            this.question = question;
        }

        public Builder questionPresetId(String value) {
            this.questionPresetId = value;
            return this;
        }

        public Builder contractVersion(String value) {
            this.contractVersion = value;
            return this;
        }

        public Builder window(ConversationWindow value) {
            this.window = value;
            return this;
        }

        public Builder projectSlug(String value) {
            this.projectSlug = value;
            return this;
        }

        public Builder caseSlug(String value) {
            this.caseSlug = value;
            return this;
        }

        public Builder recommendationContext(PortfolioRecommendationContext value) {
            this.recommendationContext = value;
            return this;
        }

        public Builder referenceContext(PortfolioReferenceContext value) {
            this.referenceContext = value;
            return this;
        }

        public Builder audienceRole(String value) {
            this.audienceRole = value;
            return this;
        }

        public Builder source(String value) {
            this.source = value;
            return this;
        }

        public PortfolioTurn build() {
            return new PortfolioTurn(this);
        }
    }
}
