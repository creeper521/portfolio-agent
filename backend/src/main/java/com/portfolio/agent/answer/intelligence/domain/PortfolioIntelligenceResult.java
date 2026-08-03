package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioIntelligenceResult {

    private final PortfolioTaskMode resolvedIntent;
    private final List<PortfolioRetrievedSubject> subjects;
    private final List<PortfolioRetrievedPassage> evidence;
    private final PortfolioRecommendation portfolioRecommendation;
    private final PortfolioClarification clarification;
    private final String contentVersion;
    private final boolean degraded;
    private final String noticeCode;
    private final AnswerIntentSource intentSource;
    private final boolean contextVersionUpdated;

    public PortfolioIntelligenceResult(
            PortfolioTaskMode resolvedIntent,
            List<PortfolioRetrievedSubject> subjects,
            List<PortfolioRetrievedPassage> evidence,
            PortfolioRecommendation portfolioRecommendation,
            PortfolioClarification clarification,
            boolean degraded,
            String noticeCode) {
        this(resolvedIntent, subjects, evidence, portfolioRecommendation, clarification,
                null, degraded, noticeCode, null, false);
    }

    public PortfolioIntelligenceResult(
            PortfolioTaskMode resolvedIntent,
            List<PortfolioRetrievedSubject> subjects,
            List<PortfolioRetrievedPassage> evidence,
            PortfolioRecommendation portfolioRecommendation,
            PortfolioClarification clarification,
            String contentVersion,
            boolean degraded,
            String noticeCode) {
        this(resolvedIntent, subjects, evidence, portfolioRecommendation, clarification,
                contentVersion, degraded, noticeCode, null, false);
    }

    public PortfolioIntelligenceResult(
            PortfolioTaskMode resolvedIntent,
            List<PortfolioRetrievedSubject> subjects,
            List<PortfolioRetrievedPassage> evidence,
            PortfolioRecommendation portfolioRecommendation,
            PortfolioClarification clarification,
            String contentVersion,
            boolean degraded,
            String noticeCode,
            AnswerIntentSource intentSource,
            boolean contextVersionUpdated) {
        this.resolvedIntent = Objects.requireNonNull(resolvedIntent, "resolvedIntent");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        this.portfolioRecommendation = portfolioRecommendation;
        this.clarification = clarification;
        this.contentVersion = contentVersion == null || contentVersion.isBlank()
                ? null
                : contentVersion.trim();
        this.degraded = degraded;
        this.noticeCode = noticeCode == null || noticeCode.isBlank() ? null : noticeCode.trim();
        this.intentSource = intentSource;
        this.contextVersionUpdated = contextVersionUpdated;
        if (resolvedIntent == PortfolioTaskMode.CLARIFICATION_REQUIRED && clarification == null) {
            throw new IllegalArgumentException("clarification is required for CLARIFICATION_REQUIRED");
        }
        if (resolvedIntent != PortfolioTaskMode.CLARIFICATION_REQUIRED && clarification != null) {
            throw new IllegalArgumentException("clarification is only allowed for CLARIFICATION_REQUIRED");
        }
    }

    public static PortfolioIntelligenceResult clarification(PortfolioClarification clarification) {
        return new PortfolioIntelligenceResult(
                PortfolioTaskMode.CLARIFICATION_REQUIRED, List.of(), List.of(), null,
                clarification, false, null);
    }

    public PortfolioTaskMode getResolvedIntent() { return resolvedIntent; }
    public List<PortfolioRetrievedSubject> getSubjects() { return subjects; }
    public List<PortfolioRetrievedPassage> getEvidence() { return evidence; }
    public PortfolioRecommendation getPortfolioRecommendation() { return portfolioRecommendation; }
    public PortfolioClarification getClarification() { return clarification; }
    public String getContentVersion() { return contentVersion; }
    public boolean isDegraded() { return degraded; }
    public String getNoticeCode() { return noticeCode; }
    public AnswerIntentSource getIntentSource() { return intentSource; }
    public boolean isContextVersionUpdated() { return contextVersionUpdated; }

    public PortfolioIntelligenceResult withDecisionMetadata(
            AnswerIntentSource source,
            boolean versionUpdated
    ) {
        return new PortfolioIntelligenceResult(
                resolvedIntent,
                subjects,
                evidence,
                portfolioRecommendation,
                clarification,
                contentVersion,
                degraded,
                noticeCode,
                Objects.requireNonNull(source, "source"),
                versionUpdated);
    }
}
