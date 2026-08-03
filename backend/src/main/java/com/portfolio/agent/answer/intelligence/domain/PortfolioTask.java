package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioTask {

    private final String turnId;
    private final String question;
    private final PortfolioTaskMode mode;
    private final double confidence;
    private final PortfolioConditions conditions;
    private final PortfolioRecommendationContext recommendationContext;
    private final PortfolioRefinement refinement;
    private final String subjectId;

    public PortfolioTask(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement) {
        this(turnId, question, mode, confidence, conditions,
                recommendationContext, refinement, null);
    }

    public PortfolioTask(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement,
            String subjectId) {
        this.turnId = requireText(turnId, "turnId");
        this.question = requireText(question, "question");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(confidence) || confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        this.confidence = confidence;
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.recommendationContext = recommendationContext;
        this.refinement = refinement;
        this.subjectId = normalizeText(subjectId);
    }

    public String getTurnId() { return turnId; }
    public String getQuestion() { return question; }
    public PortfolioTaskMode getMode() { return mode; }
    public double getConfidence() { return confidence; }
    public PortfolioConditions getConditions() { return conditions; }
    public PortfolioRecommendationContext getRecommendationContext() { return recommendationContext; }
    public PortfolioRefinement getRefinement() { return refinement; }
    public String getSubjectId() { return subjectId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioTask that)) { return false; }
        return Double.compare(confidence, that.confidence) == 0
                && Objects.equals(turnId, that.turnId)
                && Objects.equals(question, that.question)
                && mode == that.mode
                && Objects.equals(conditions, that.conditions)
                && Objects.equals(recommendationContext, that.recommendationContext)
                && Objects.equals(refinement, that.refinement)
                && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turnId, question, mode, confidence, conditions,
                recommendationContext, refinement, subjectId);
    }

    @Override
    public String toString() {
        return "PortfolioTask{" + "turnId='" + turnId + '\''
                + ", question='<redacted>'"
                + ", mode=" + mode + ", confidence=" + confidence
                + ", hasRecommendationContext=" + (recommendationContext != null)
                + ", hasRefinement=" + (refinement != null)
                + ", hasSubjectConstraint=" + (subjectId != null) + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
