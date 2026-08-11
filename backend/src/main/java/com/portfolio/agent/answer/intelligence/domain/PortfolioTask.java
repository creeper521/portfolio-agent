package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;

public final class PortfolioTask {

    private final String turnId;
    private final String question;
    private final PortfolioTaskMode mode;
    private final double confidence;
    private final PortfolioConditions conditions;
    private final PortfolioRecommendationContext recommendationContext;
    private final PortfolioRefinement refinement;
    private final List<String> subjectIds;
    private final List<AnswerClaimCategory> preferredClaimCategories;

    public PortfolioTask(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement) {
        this(turnId, question, mode, confidence, conditions,
                recommendationContext, refinement, (String) null, List.of());
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
        this(turnId, question, mode, confidence, conditions,
                recommendationContext, refinement, subjectId, List.of());
    }

    public PortfolioTask(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement,
            String subjectId,
            List<AnswerClaimCategory> preferredClaimCategories) {
        this(turnId, question, mode, confidence, conditions, recommendationContext, refinement,
                subjectId == null || subjectId.isBlank() ? List.of() : List.of(subjectId),
                preferredClaimCategories);
    }

    public PortfolioTask(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement,
            List<String> subjectIds,
            List<AnswerClaimCategory> preferredClaimCategories) {
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
        this.subjectIds = copySubjectIds(subjectIds);
        this.preferredClaimCategories = List.copyOf(Objects.requireNonNull(
                preferredClaimCategories, "preferredClaimCategories"));
    }

    public String getTurnId() { return turnId; }
    public String getQuestion() { return question; }
    public PortfolioTaskMode getMode() { return mode; }
    public double getConfidence() { return confidence; }
    public PortfolioConditions getConditions() { return conditions; }
    public PortfolioRecommendationContext getRecommendationContext() { return recommendationContext; }
    public PortfolioRefinement getRefinement() { return refinement; }
    public String getSubjectId() { return subjectIds.size() == 1 ? subjectIds.getFirst() : null; }
    public List<String> getSubjectIds() { return subjectIds; }
    public List<AnswerClaimCategory> getPreferredClaimCategories() {
        return preferredClaimCategories;
    }

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
                && Objects.equals(subjectIds, that.subjectIds)
                && Objects.equals(preferredClaimCategories, that.preferredClaimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turnId, question, mode, confidence, conditions,
                recommendationContext, refinement, subjectIds, preferredClaimCategories);
    }

    @Override
    public String toString() {
        return "PortfolioTask{" + "turnId='" + turnId + '\''
                + ", question='<redacted>'"
                + ", mode=" + mode + ", confidence=" + confidence
                + ", hasRecommendationContext=" + (recommendationContext != null)
                + ", hasRefinement=" + (refinement != null)
                + ", subjectConstraintCount=" + subjectIds.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> copySubjectIds(List<String> values) {
        Objects.requireNonNull(values, "subjectIds");
        List<String> copied = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = requireText(value, "subjectIds");
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException("subjectIds must not contain duplicates");
            }
            copied.add(normalized);
        }
        return List.copyOf(copied);
    }
}
